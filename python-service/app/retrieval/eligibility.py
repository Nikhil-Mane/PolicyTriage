"""Eligibility filtering over the policy corpus.

Eligibility is purely structural (tenant, role, approval state, effective
date window) and never inspects record text. This is what keeps embedded
instructions inside a policy record's `text` field from ever being able to
influence which records a caller can see.
"""
from datetime import date

Policy = dict


def is_eligible(record: Policy, tenant: str, role: str, as_of: date) -> bool:
    if record["tenant"] != tenant or record["role"] != role:
        return False
    if record["approval_state"] != "Approved":
        return False
    effective_from = date.fromisoformat(record["effective_from"])
    effective_to = date.fromisoformat(record["effective_to"])
    return effective_from <= as_of < effective_to


def filter_eligible(records: list[Policy], tenant: str, role: str, as_of: date) -> list[Policy]:
    return [r for r in records if is_eligible(r, tenant, role, as_of)]
