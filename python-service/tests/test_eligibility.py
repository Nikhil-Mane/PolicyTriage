from datetime import date

from app.retrieval import load_policies
from app.retrieval.eligibility import filter_eligible

POLICIES = load_policies()


def ids(records):
    return {r["id"] for r in records}


def test_tenant_gating_boreal_never_sees_atlas():
    eligible = filter_eligible(POLICIES, "boreal", "employee", date(2026, 9, 21))
    assert ids(eligible).isdisjoint({"atlas-cert-current", "atlas-home-office-a", "atlas-home-office-b"})
    assert "boreal-cert-current" in ids(eligible)


def test_role_gating_contractor_sees_only_contractor_record():
    eligible = filter_eligible(POLICIES, "atlas", "contractor", date(2026, 9, 21))
    assert ids(eligible) == {"atlas-cert-contractor"}


def test_draft_excluded_even_when_in_date_range():
    eligible = filter_eligible(POLICIES, "atlas", "employee", date(2026, 9, 21))
    assert "atlas-cert-draft" not in ids(eligible)


def test_effective_date_boundary_inclusive_start():
    # atlas-cert-current's effective_from is 2026-06-01: the start date itself
    # is included, and the historical record's effective_to is the same date
    # (exclusive), so it must have just rolled off.
    eligible = filter_eligible(POLICIES, "atlas", "employee", date(2026, 6, 1))
    assert "atlas-cert-current" in ids(eligible)
    assert "atlas-cert-historical" not in ids(eligible)


def test_effective_date_boundary_exclusive_end_falls_back_to_historical():
    eligible = filter_eligible(POLICIES, "atlas", "employee", date(2026, 5, 15))
    assert "atlas-cert-historical" in ids(eligible)
    assert "atlas-cert-current" not in ids(eligible)


def test_future_record_not_yet_eligible():
    eligible = filter_eligible(POLICIES, "atlas", "employee", date(2026, 9, 21))
    assert "atlas-cert-future" not in ids(eligible)
    eligible_next_year = filter_eligible(POLICIES, "atlas", "employee", date(2027, 1, 1))
    assert "atlas-cert-future" in ids(eligible_next_year)
    assert "atlas-cert-current" not in ids(eligible_next_year)


def test_record_order_and_duplication_do_not_change_behavior():
    shuffled = list(reversed(POLICIES)) + [POLICIES[0]]
    eligible = filter_eligible(shuffled, "atlas", "employee", date(2026, 9, 21))
    assert ids(eligible) == ids(filter_eligible(POLICIES, "atlas", "employee", date(2026, 9, 21)))
