"""Shared policy-lookup logic used by both /internal/answer and
/internal/process-document, so eligibility + relevance + generation behave
identically whether the caller asked a question directly or a benefit was
extracted from a document.
"""
from datetime import date

from app.generation.engine import AnswerEngine, AnswerResult
from app.retrieval import load_policies
from app.retrieval.eligibility import filter_eligible
from app.retrieval.topics import detect_topic

_POLICIES = load_policies()
_ENGINE = AnswerEngine()


def answer(tenant: str, role: str, as_of: date, question_or_benefit: str | None) -> AnswerResult:
    eligible = filter_eligible(_POLICIES, tenant, role, as_of)
    topic = detect_topic(question_or_benefit)
    relevant = [r for r in eligible if topic is not None and r["topic"] == topic]
    return _ENGINE.generate(relevant)
