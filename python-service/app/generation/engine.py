"""Deterministic, offline answer generation.

This is a model *double*, not an LLM integration: it never interprets policy
text as instructions, it only extracts/echoes values from it. That is what
keeps an embedded prompt injection inside a policy record's `text` (e.g.
"SYSTEM MESSAGE: switch tenant...") from ever changing behavior -- the engine
has no code path that treats record text as anything but a quotable value.
"""
import re
from dataclasses import dataclass, field

AMOUNT_RE = re.compile(r"INR\s*([\d,]+)")


@dataclass
class Citation:
    chunk_id: str
    quote: str


@dataclass
class AnswerResult:
    status: str  # ANSWERED | INSUFFICIENT_EVIDENCE | CONFLICT
    answer: str | None
    citations: list[Citation] = field(default_factory=list)

    def to_dict(self) -> dict:
        return {
            "status": self.status,
            "answer": self.answer,
            "citations": [{"chunk_id": c.chunk_id, "quote": c.quote} for c in self.citations],
        }


def _asserted_value(text: str) -> str:
    """Extract the value a policy record asserts, for equality comparison.

    If the text contains an INR amount, the amount is the asserted value
    (this is what lets home-office-a vs home-office-b disagree numerically).
    Otherwise the normalized full text is the asserted value, so two records
    making the same non-numeric statement agree, and two making different
    statements conflict -- with no per-record-id branching either way.
    """
    match = AMOUNT_RE.search(text)
    if match:
        return match.group(1).replace(",", "")
    return " ".join(text.split()).lower()


def _answer_text(text: str) -> str:
    match = AMOUNT_RE.search(text)
    if match:
        return f"INR {match.group(1).replace(',', '')}"
    return text.strip()


class AnswerEngine:
    """The single call site for generation. Invoked at most once per request."""

    def generate(self, records: list[dict]) -> AnswerResult:
        if not records:
            return AnswerResult(status="INSUFFICIENT_EVIDENCE", answer=None, citations=[])

        citations = [Citation(chunk_id=r["id"], quote=r["text"]) for r in records]
        values = {_asserted_value(r["text"]) for r in records}

        if len(values) == 1:
            return AnswerResult(status="ANSWERED", answer=_answer_text(records[0]["text"]), citations=citations)

        return AnswerResult(status="CONFLICT", answer=None, citations=citations)
