# PolicyTriage

A local, two-service application for a multi-tenant employee support desk:

- **`spring-service`** — Spring Boot (Java 21, Maven) public API. Owns caller
  identity, input validation, calling Python with a bounded timeout, and
  assembling the public response shapes.
- **`python-service`** — FastAPI internal service. Owns document extraction,
  policy eligibility/relevance retrieval, and deterministic offline answer
  generation. Never reachable directly by clients.

## Trust boundary

Spring resolves `X-Caller-Id` to a `(tenant, role)` pair from a static
registry and passes that, plus a validated `as_of` date, to Python on every
call. Python trusts those three values as given and never re-derives them
from policy or document text — this is what keeps an embedded instruction
inside a policy record or a submitted document from ever changing who a
caller is or what they're allowed to see.

## Running it

### Docker Compose (recommended)

```bash
docker compose up --build
```

- Spring: `http://localhost:8080`
- Python (internal, exposed here for convenience/debugging only): `http://localhost:8000`

### Running each service directly

```bash
# Python service
cd python-service
python -m venv .venv && .venv/Scripts/activate  # or source .venv/bin/activate on macOS/Linux
pip install -e ".[dev]"
uvicorn app.main:app --reload --port 8000

# Spring service (in another terminal)
cd spring-service
mvn spring-boot:run
```

## Callers (static registry)

| X-Caller-Id              | Tenant | Role       |
|---------------------------|--------|------------|
| `atlas-employee-01`   | atlas  | employee   |
| `atlas-contractor-01` | atlas  | contractor |
| `boreal-employee-01`  | boreal | employee   |

## API

### `POST /answer`

Headers: `X-Caller-Id` (required)
Body:
```json
{"as_of": "2026-09-21", "question": "What is the certification reimbursement cap?"}
```
Response:
```json
{"status": "ANSWERED" | "INSUFFICIENT_EVIDENCE" | "CONFLICT",
 "answer": "string or null",
 "citations": [{"chunk_id": "atlas-cert-current", "quote": "verbatim source text"}]}
```

### `POST /batches`

Headers: `X-Caller-Id` (required)
Multipart form:
- `metadata` — JSON part (content-type `application/json`) with the batch manifest
- `files` — one part per document, repeated (`-F "files=@doc1.txt" -F "files=@doc2.pdf"`)

Manifest shape:
```json
{"batch_id": "demo-01", "as_of": "2026-09-21",
 "documents": [{"document_id": "request-01", "filename": "request-01.txt"}]}
```

Response:
```json
{"batch_id": "demo-01",
 "summary": {"total": 8, "completed": 7, "failed": 1},
 "results": [
   {"document_id": "request-01",
    "processing_status": "COMPLETED" | "FAILED",
    "extracted": {"benefit": "...", "amount": 18000, "currency": "INR", "reference": "CERT-101"},
    "field_evidence": {"benefit": {"quote": "..."}, "amount": {"quote": "..."}, "reference": {"quote": "..."}},
    "policy": { "...": "same shape as /answer response" } ,
    "review_required": true,
    "issues": ["..."],
    "duplicate_of": null,
    "error": null}
 ]}
```

### Error / status mapping

| Status | Cause |
|--------|-------|
| 401 | Missing or unknown `X-Caller-Id` |
| 400 | Malformed `as_of`, empty `question`, malformed batch metadata JSON, duplicate manifest `document_id`s, missing/extra file parts |
| 502 | (`/answer` only) Python unreachable or returned a malformed response |
| 504 | (`/answer` only) Python call exceeded its bounded timeout |

Item-level `error.code` values inside a batch result (never fail the whole
batch): `EMPTY_FILE`, `UNREADABLE_FILE`, `MODEL_TIMEOUT`, `MODEL_UNAVAILABLE`,
`MALFORMED_MODEL_OUTPUT`.

## Policy corpus and retrieval

`python-service/app/data/policies.json` holds the 12-record corpus verbatim.
Eligibility = `tenant matches AND role matches AND approval_state == "Approved"
AND effective_from <= as_of < effective_to` (inclusive start, exclusive end;
`Draft` records are always excluded). Relevance is a small keyword-based
topic tagger (`certification`, `home-office`, `training`, `travel`) applied
identically to questions, extracted document benefits, and policy text.
Generation: 0 relevant eligible records -> `INSUFFICIENT_EVIDENCE`; all
relevant eligible records assert the same value -> `ANSWERED`; they disagree
-> `CONFLICT` with no invented precedence rule.

Benefit extraction first looks for an explicit `Benefit: ...` label, and
falls back to scanning sentences for the same keyword vocabulary (e.g. "I
request certification reimbursement..." -> `Certification`) when there is
none, since the supplied request documents describe the benefit in prose
rather than a label. A benefit the corpus has no policy for (e.g. a gym
membership) is still extracted and reported — `INSUFFICIENT_EVIDENCE` is a
retrieval outcome, not a reason to hide what was asked for.

## Testing

```bash
# Python
cd python-service && pip install -e ".[dev]" && pytest

# Spring
cd spring-service && mvn test
```

## Examples

See `examples/` for runnable curl scripts against the three `/answer`
statuses and the full 8-document `/batches` run (`examples/manifest.json` +
`fixtures/requests/`).

## Explicitly out of scope for tests

The keyword-based topic tagger is not robust to heavy paraphrase or a
question naming multiple benefits at once (e.g. "what about training and
travel?"). A question that doesn't contain one of the configured keywords
falls through to `INSUFFICIENT_EVIDENCE` even when a human reader would
recognize the intent. This is a known, accepted limitation — see
`DECISION_NOTE.md`.

## Further reading

- `DECISION_NOTE.md` — the retrieval design decision, its trade-offs, and
  what's unfinished.
- `PRODUCTION_DESIGN.md` — deployment approach and top security/operational
  risks for a real rollout.
- `ASSESSMENT_GUIDE.md` — a walkthrough of every request path, the corpus,
  each of the 8 fixture requests and its expected outcome, and answers to
  the kind of "trace a request" / "predict the effect of a change"
  questions the technical follow-up asks.
