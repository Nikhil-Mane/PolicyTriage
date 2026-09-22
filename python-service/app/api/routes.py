import logging
import uuid
from datetime import date

from fastapi import APIRouter, Form, Header, HTTPException, UploadFile

from app.api import service
from app.api.schemas import AnswerRequest, AnswerResponse
from app.extraction.fields import extract_fields
from app.extraction.text_extract import EmptyFileError, UnreadableFileError, extract_text
from app.generation.errors import MalformedModelOutputError, ModelTimeoutError, ModelUnavailableError

router = APIRouter(prefix="/internal")
logger = logging.getLogger("policy_triage")

UNAPPROVED_ACTION_MARKERS = (
    "without manager approval",
    "without approval",
    "without prior approval",
    "have not obtained manager approval",
    "not obtained manager approval",
    "no manager approval",
)


@router.post("/answer", response_model=AnswerResponse)
def internal_answer(payload: AnswerRequest, x_request_id: str | None = Header(default=None)) -> AnswerResponse:
    request_id = x_request_id or str(uuid.uuid4())

    try:
        as_of = date.fromisoformat(payload.as_of)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail="as_of must be an ISO date (YYYY-MM-DD)") from exc

    try:
        result = service.answer(payload.tenant, payload.role, as_of, payload.question)
    except (ModelTimeoutError, ModelUnavailableError, MalformedModelOutputError) as exc:
        logger.error("request_id=%s answer_failed error=%s", request_id, exc)
        raise HTTPException(status_code=502, detail=str(exc)) from exc

    chunk_ids = [c.chunk_id for c in result.citations]
    logger.info("request_id=%s status=%s chunk_ids=%s", request_id, result.status, chunk_ids)
    return AnswerResponse(**result.to_dict())


@router.post("/process-document")
async def internal_process_document(
    file: UploadFile,
    tenant: str = Form(...),
    role: str = Form(...),
    as_of: str = Form(...),
    x_batch_id: str | None = Header(default=None),
    x_document_id: str | None = Header(default=None),
) -> dict:
    log_ctx = f"batch_id={x_batch_id} document_id={x_document_id}"
    content = await file.read()
    filename = file.filename or ""

    try:
        text = extract_text(filename, content)
    except EmptyFileError as exc:
        logger.warning("%s status=FAILED error=EMPTY_FILE", log_ctx)
        return _failed("EMPTY_FILE", str(exc))
    except UnreadableFileError as exc:
        logger.warning("%s status=FAILED error=UNREADABLE_FILE", log_ctx)
        return _failed("UNREADABLE_FILE", str(exc))

    extraction = extract_fields(text)
    issues = list(extraction.issues)

    policy = None
    if extraction.benefit.value is not None:
        try:
            as_of_date = date.fromisoformat(as_of)
            result = service.answer(tenant, role, as_of_date, extraction.benefit.value)
        except ModelTimeoutError as exc:
            logger.warning("%s status=FAILED error=MODEL_TIMEOUT", log_ctx)
            return _failed("MODEL_TIMEOUT", str(exc))
        except ModelUnavailableError as exc:
            logger.warning("%s status=FAILED error=MODEL_UNAVAILABLE", log_ctx)
            return _failed("MODEL_UNAVAILABLE", str(exc))
        except MalformedModelOutputError as exc:
            logger.warning("%s status=FAILED error=MALFORMED_MODEL_OUTPUT", log_ctx)
            return _failed("MALFORMED_MODEL_OUTPUT", str(exc))

        policy = result.to_dict()
        issues.extend(_policy_issues(policy, extraction))
    else:
        issues.append("Benefit could not be identified, so no policy lookup was performed.")

    if any(marker in text.lower() for marker in UNAPPROVED_ACTION_MARKERS):
        issues.append(
            "Document narrative references an action taken without approval; this claim is unverified "
            "and requires manual review."
        )

    chunk_ids = [c["chunk_id"] for c in policy["citations"]] if policy else []
    logger.info("%s status=COMPLETED policy_status=%s chunk_ids=%s", log_ctx, policy["status"] if policy else None, chunk_ids)

    return {
        "processing_status": "COMPLETED",
        "extracted": {
            "benefit": extraction.benefit.value,
            "amount": extraction.amount.value,
            "currency": extraction.currency.value,
            "reference": extraction.reference.value,
        },
        "field_evidence": {
            "benefit": {"quote": extraction.benefit.quote},
            "amount": {"quote": extraction.amount.quote},
            "reference": {"quote": extraction.reference.quote},
        },
        "policy": policy,
        "review_required": True,
        "issues": issues,
        "error": None,
    }


def _policy_issues(policy: dict, extraction) -> list[str]:
    issues = []
    if policy["status"] == "ANSWERED" and extraction.amount.value is not None and str(policy["answer"]).startswith("INR"):
        issues.append(
            f"Policy states a limit ({policy['answer']}); this does not confirm the requested amount "
            f"(INR {extraction.amount.value}) is payable."
        )
    if policy["status"] == "INSUFFICIENT_EVIDENCE":
        issues.append(f"No matching policy found for benefit '{extraction.benefit.value}'.")
    if policy["status"] == "CONFLICT":
        issues.append("Applicable policy records disagree; amount cannot be confirmed automatically.")
    return issues


def _failed(code: str, message: str) -> dict:
    return {
        "processing_status": "FAILED",
        "extracted": None,
        "field_evidence": None,
        "policy": None,
        "review_required": True,
        "issues": [],
        "error": {"code": code, "message": message},
    }
