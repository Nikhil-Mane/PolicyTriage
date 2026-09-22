import io
from pathlib import Path

import pytest
from pypdf import PdfWriter

from app.extraction.fields import extract_fields
from app.extraction.text_extract import EmptyFileError, UnreadableFileError, extract_text

FIXTURES = Path(__file__).resolve().parent.parent.parent / "fixtures" / "requests"


def test_txt_extraction_is_clean():
    text = extract_text("request-01.txt", b"Benefit: Certification\nAmount: INR 18000\nReference: CERT-101\n")
    assert "Certification" in text
    fields = extract_fields(text)
    assert fields.benefit.value == "Certification"
    assert fields.amount.value == 18000
    assert fields.reference.value == "CERT-101"
    assert fields.issues == []


def test_ambiguous_dual_amount_is_left_null_with_both_quotes_surfaced():
    text = (
        "Benefit: Certification\nAmount: INR 22000\nReference: CERT-900\n"
        "Details: After tax adjustment the reimbursable amount becomes INR 28000."
    )
    fields = extract_fields(text)
    assert fields.amount.value is None
    assert fields.amount.quote is None
    assert any("ambiguous" in issue.lower() for issue in fields.issues)
    assert any("22000" in issue for issue in fields.issues)
    assert any("28000" in issue for issue in fields.issues)


def test_missing_amount_is_null_not_guessed():
    text = "Benefit: Training\nReference: TRN-900\nDetails: Attended external training program."
    fields = extract_fields(text)
    assert fields.amount.value is None
    assert any("missing" in issue.lower() for issue in fields.issues)


def test_zero_byte_file_raises_empty_file_error():
    with pytest.raises(EmptyFileError):
        extract_text("request-08.txt", b"")


def test_pdf_with_no_text_layer_raises_unreadable_file_error():
    writer = PdfWriter()
    writer.add_blank_page(width=200, height=200)
    buf = io.BytesIO()
    writer.write(buf)

    with pytest.raises(UnreadableFileError):
        extract_text("blank.pdf", buf.getvalue())


def test_real_pdf_fixture_extracts_home_office_fields():
    content = (FIXTURES / "request-02.pdf").read_bytes()
    text = extract_text("request-02.pdf", content)
    fields = extract_fields(text)
    assert fields.benefit.value == "Home Office"
    assert fields.amount.value == 14000
    assert fields.reference.value == "HOME-202"


def test_benefit_is_inferred_from_free_text_without_a_label():
    # request-01.txt has no explicit "Benefit: ..." line -- the benefit has
    # to be read out of the sentence describing what's being claimed.
    text = (
        "Reference: CERT-101\n"
        "I request certification reimbursement of INR 18000 for a completed cloud certification."
    )
    fields = extract_fields(text)
    assert fields.benefit.value == "Certification"
    assert "certification reimbursement" in fields.benefit.quote.lower()
    assert fields.reference.value == "CERT-101"


def test_benefit_not_covered_by_any_policy_is_still_readable_not_null():
    # A benefit the corpus has no policy for (gym/wellness) must still be
    # extracted and surfaced -- "no policy exists" is a retrieval outcome,
    # not a reason to hide what the caller actually asked for.
    text = "Reference: WELL-404\nI request reimbursement of INR 6000 for a gym membership."
    fields = extract_fields(text)
    assert fields.benefit.value == "Wellness"
    assert fields.issues == []  # benefit was found; only the policy lookup later finds nothing


def test_no_recognizable_benefit_is_null_with_issue():
    text = "Reference: MISC-001\nI am submitting a general expense claim for INR 500."
    fields = extract_fields(text)
    assert fields.benefit.value is None
    assert any("benefit could not be determined" in issue.lower() for issue in fields.issues)
