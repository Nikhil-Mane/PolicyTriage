"""Field extraction with source quotes from a request document's raw text.

Every extracted field is traceable to the exact line it came from. Fields
that cannot be determined unambiguously (missing, or asserted more than one
way in the same document) are returned as null rather than guessed, with the
conflicting evidence surfaced in `issues` instead.
"""
import re
from dataclasses import dataclass

AMOUNT_RE = re.compile(r"INR\s*([\d,]+)")
INJECTION_MARKERS = ("system message", "ignore all previous instructions", "ignore previous instructions")

_LABELS = {
    "benefit": re.compile(r"^\s*Benefit\s*:\s*(.+)$", re.IGNORECASE | re.MULTILINE),
    "currency": re.compile(r"^\s*Currency\s*:\s*(.+)$", re.IGNORECASE | re.MULTILINE),
    "reference": re.compile(r"^\s*Reference\s*:\s*(.+)$", re.IGNORECASE | re.MULTILINE),
}

# Fallback benefit detection for free-text requests that describe what they
# want in a sentence (e.g. "I request certification reimbursement of INR
# 18000...") rather than an explicit "Benefit: ..." label. First match wins.
# "Wellness" deliberately shares no keyword with any policy topic, so a
# benefit is still surfaced for the caller to read even though no policy in
# the corpus covers it.
_BENEFIT_KEYWORDS: tuple[tuple[str, tuple[str, ...]], ...] = (
    ("Certification", ("certificat",)),
    ("Home Office", ("home office", "home-office")),
    ("Training", ("training", "course", "workshop")),
    ("Travel", ("travel", "business trip")),
    ("Wellness", ("gym", "wellness", "fitness")),
)


@dataclass
class FieldResult:
    value: str | int | None
    quote: str | None


@dataclass
class ExtractionResult:
    benefit: FieldResult
    amount: FieldResult
    currency: FieldResult
    reference: FieldResult
    issues: list[str]


def _labeled_field(text: str, label: str) -> FieldResult:
    match = _LABELS[label].search(text)
    if not match:
        return FieldResult(value=None, quote=None)
    return FieldResult(value=match.group(1).strip(), quote=match.group(0).strip())


def _split_sentences(text: str) -> list[str]:
    # Drop labeled lines (e.g. "Reference: CERT-101") before joining, so they
    # never glue onto the following sentence; then collapse all remaining
    # whitespace/line-wrapping (PDF text extraction wraps mid-sentence) into
    # single spaces before splitting on sentence-ending punctuation.
    body = text
    for pattern in _LABELS.values():
        body = pattern.sub("", body)
    normalized = " ".join(body.split())
    return [s.strip() for s in re.split(r"(?<=[.?!])\s+", normalized) if s.strip()]


def _infer_benefit(text: str) -> FieldResult:
    for sentence in _split_sentences(text):
        lowered = sentence.lower()
        for label, keywords in _BENEFIT_KEYWORDS:
            if any(keyword in lowered for keyword in keywords):
                return FieldResult(value=label, quote=sentence)
    return FieldResult(value=None, quote=None)


def _extract_amount(text: str) -> tuple[FieldResult, list[str]]:
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    matches: list[tuple[str, str]] = []  # (normalized value, source line)
    for line in lines:
        for m in AMOUNT_RE.finditer(line):
            matches.append((m.group(1).replace(",", ""), line))

    distinct_values = {value for value, _ in matches}
    if not matches:
        return FieldResult(value=None, quote=None), []
    if len(distinct_values) > 1:
        first_line_per_value = dict(matches)  # last-wins by value, but each value maps to one of its lines
        quotes = ", ".join(f"'{line}'" for line in first_line_per_value.values())
        issue = f"Amount is ambiguous: conflicting values found ({quotes}); left unset for manual review."
        return FieldResult(value=None, quote=None), [issue]

    value, source_line = matches[0]
    return FieldResult(value=int(value), quote=source_line), []


def extract_fields(text: str) -> ExtractionResult:
    issues: list[str] = []

    benefit = _labeled_field(text, "benefit")
    if benefit.value is None:
        benefit = _infer_benefit(text)
    if benefit.value is None:
        issues.append("Benefit could not be determined from the document.")

    amount, amount_issues = _extract_amount(text)
    issues.extend(amount_issues)
    if amount.value is None and not amount_issues:
        issues.append("Amount is missing from the document.")

    currency = _labeled_field(text, "currency")
    if currency.value is None and amount.quote:
        currency = FieldResult(value="INR", quote=amount.quote)

    reference = _labeled_field(text, "reference")

    lowered = text.lower()
    if any(marker in lowered for marker in INJECTION_MARKERS):
        issues.append("Document contained an embedded instruction; it was treated as inert text and ignored.")

    return ExtractionResult(benefit=benefit, amount=amount, currency=currency, reference=reference, issues=issues)
