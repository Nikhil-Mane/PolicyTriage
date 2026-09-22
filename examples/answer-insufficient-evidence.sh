#!/usr/bin/env bash
# INSUFFICIENT_EVIDENCE example: no policy record matches the topic at all.
curl -sS -X POST http://localhost:8080/answer \
  -H "X-Caller-Id: atlas-employee-01" \
  -H "Content-Type: application/json" \
  -d '{"as_of": "2026-09-21", "question": "Can I claim my gym membership as a wellness benefit?"}'
