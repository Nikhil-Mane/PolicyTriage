#!/usr/bin/env bash
# ANSWERED example: single eligible+relevant record.
curl -sS -X POST http://localhost:8080/answer \
  -H "X-Caller-Id: atlas-employee-01" \
  -H "Content-Type: application/json" \
  -d '{"as_of": "2026-09-21", "question": "What is the certification reimbursement cap?"}'
