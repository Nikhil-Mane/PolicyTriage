#!/usr/bin/env bash
# CONFLICT example: two eligible records disagree on the home-office amount.
curl -sS -X POST http://localhost:8080/answer \
  -H "X-Caller-Id: atlas-employee-01" \
  -H "Content-Type: application/json" \
  -d '{"as_of": "2026-09-21", "question": "What is the home office equipment allowance?"}'
