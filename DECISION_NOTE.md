# Decision Note

## The consequential choice: keyword/topic retrieval, not embeddings

Relevance matching (question/benefit -> policy record) is done with a small
fixed keyword-to-topic table (`python-service/app/retrieval/topics.py`),
applied identically to questions, extracted document benefits, and the
policy records themselves. There is no embedding model, vector index, or
similarity score anywhere in the system.

**Rejected alternative:** embedding-based semantic retrieval (e.g. a local
sentence-transformer + cosine similarity over the 12 policy chunks). This
would generalize better to paraphrased questions, but it:

- breaks the "fully offline, deterministic, auditable" requirement — a
  model's embedding output for the same text can drift across versions,
  and similarity thresholds are not something you can point to and defend
  the way a keyword table is;
- adds a real dependency (a model file, an inference runtime) to what is
  supposed to be a simple, explainable double standing in for a real LLM;
- adds a second injection surface: embedding-based retrieval scores
  arbitrary text similarity, so a policy record or document engineered to
  be "semantically close" to unrelated questions is a more direct
  gaming vector than a fixed keyword list is.

For a 12-record corpus with a fixed set of benefit types, a keyword table is
sufficient and every retrieval decision is traceable to a specific keyword
match, which is what the grading criteria ask for.

## Main limitation

The topic tagger is brittle to phrasing. A question that doesn't contain one
of the configured keywords (e.g. "certificat", "home office", "training",
"travel") will not match any topic and falls through to
`INSUFFICIENT_EVIDENCE`, even if a human would recognize it as an obvious
paraphrase ("what do you pay for professional exams?"). This is stated
explicitly in the README as an out-of-scope behavior, not something the test
suite covers.

## Time spent

Roughly one focused session: policy corpus + retrieval/generation design,
extraction rules, both services' endpoints, the 8 fixture documents, and the
test suites for both services.

## Unfinished / not attempted

- No load or concurrency testing of either service.
- No persistence layer — everything is in-memory/stateless per request, as
  the spec's scope implies.
- The Spring-side and Python-side item error codes for batch failures
  (`MODEL_TIMEOUT`, `MODEL_UNAVAILABLE`) are asserted via a controllable
  fake client/engine rather than by forcing a real network timeout, which
  is the standard way to test this deterministically but does mean the
  real WebClient timeout wiring itself isn't exercised end-to-end by an
  automated test (it is exercised manually via `docker compose up`).

## AI assistance

This implementation was built with Claude Code (Anthropic) as a pair
programmer, working from a pre-agreed implementation plan (`PLAN.md`). All
generated code was reviewed, run, and adjusted by hand where the tests
disagreed with the plan's initial design (e.g. request-03's fixture wording
was adjusted so the two conflicting amounts land on separate lines,
producing two distinct source quotes instead of one line quoted twice).

A later pass (also AI-assisted, reviewed by hand) corrected a data-fidelity
gap: the first draft's `policies.json` records and the 8 fixture requests
used different ids/dates/amounts/wording than the ones given in the
assessment PDF, even though the *scenarios* they encoded (conflict,
ambiguity, injection, missing fields) matched. That draft data has been
replaced with the assessment's literal text, ids, and dates throughout
`policies.json` and `fixtures/requests/`, and the extraction logic
(`fields.py`) was extended with a free-text benefit inference fallback,
since the PDF's own request wording (e.g. "I request certification
reimbursement of INR 18000...") has no explicit `Benefit:` label the way
the first draft's synthetic documents did. All tests were updated to match
and re-run; see the README's testing section for how to reproduce.
