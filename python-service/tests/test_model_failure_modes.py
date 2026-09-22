"""Model-double failure modes, exercised with a controllable fake engine.

These are distinct from INSUFFICIENT_EVIDENCE: they represent the
generation step itself failing, not the corpus lacking a relevant record.
Each request makes exactly one call to the engine -- there is no retry.
"""
import pytest

from app.api import service
from app.generation.errors import MalformedModelOutputError, ModelTimeoutError, ModelUnavailableError

DOC_TEXT = b"Benefit: Certification\nAmount: INR 18000\nReference: CERT-101\n"


class CountingFailingEngine:
    def __init__(self, exc: Exception):
        self.exc = exc
        self.calls = 0

    def generate(self, records):
        self.calls += 1
        raise self.exc


@pytest.mark.parametrize(
    "exc, expected_code",
    [
        (ModelTimeoutError("timed out"), "MODEL_TIMEOUT"),
        (ModelUnavailableError("unavailable"), "MODEL_UNAVAILABLE"),
        (MalformedModelOutputError("bad output"), "MALFORMED_MODEL_OUTPUT"),
    ],
)
def test_process_document_maps_model_failures_to_stable_error_codes(client, monkeypatch, exc, expected_code):
    fake_engine = CountingFailingEngine(exc)
    monkeypatch.setattr(service, "_ENGINE", fake_engine)

    response = client.post(
        "/internal/process-document",
        data={"tenant": "atlas", "role": "employee", "as_of": "2026-09-21"},
        files={"file": ("request-01.txt", DOC_TEXT, "text/plain")},
    )

    body = response.json()
    assert body["processing_status"] == "FAILED"
    assert body["error"]["code"] == expected_code
    assert fake_engine.calls == 1  # exactly one attempt, no retry
