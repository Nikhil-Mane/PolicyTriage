from datetime import date

from app.api.service import answer


def test_certification_single_eligible_record_is_answered():
    result = answer("atlas", "employee", date(2026, 9, 21), "What is the certification reimbursement cap?")
    assert result.status == "ANSWERED"
    assert result.answer == "INR 25000"
    assert [c.chunk_id for c in result.citations] == ["atlas-cert-current"]


def test_home_office_conflicting_records_is_conflict():
    result = answer("atlas", "employee", date(2026, 9, 21), "What is the home office equipment allowance?")
    assert result.status == "CONFLICT"
    assert result.answer is None
    assert {c.chunk_id for c in result.citations} == {"atlas-home-office-a", "atlas-home-office-b"}


def test_wellness_question_has_no_matching_topic_is_insufficient_evidence():
    result = answer("atlas", "employee", date(2026, 9, 21), "Can I claim my gym membership as a wellness benefit?")
    assert result.status == "INSUFFICIENT_EVIDENCE"
    assert result.answer is None
    assert result.citations == []


def test_boreal_employee_never_sees_atlas_amount():
    result = answer("boreal", "employee", date(2026, 9, 21), "What is the certification reimbursement cap?")
    assert result.status == "ANSWERED"
    assert result.answer == "INR 80000"
    assert [c.chunk_id for c in result.citations] == ["boreal-cert-current"]


def test_contractor_role_gating_sees_contractor_amount_only():
    result = answer("atlas", "contractor", date(2026, 9, 21), "What is the certification reimbursement cap?")
    assert result.status == "ANSWERED"
    assert result.answer == "INR 10000"
    assert [c.chunk_id for c in result.citations] == ["atlas-cert-contractor"]


def test_future_record_answers_once_it_takes_effect():
    result = answer("atlas", "employee", date(2027, 1, 1), "What is the certification reimbursement cap?")
    assert result.status == "ANSWERED"
    assert result.answer == "INR 35000"
    assert [c.chunk_id for c in result.citations] == ["atlas-cert-future"]


def test_travel_and_training_topics_have_single_non_numeric_answers():
    travel = answer("atlas", "employee", date(2026, 9, 21), "What is the business travel policy?")
    assert travel.status == "ANSWERED"
    assert travel.answer == "Employees may claim rail travel for approved business trips."

    training = answer("atlas", "employee", date(2026, 9, 21), "What is the external training policy?")
    assert training.status == "ANSWERED"
    assert training.answer == "Manager approval is required before external training is booked."
