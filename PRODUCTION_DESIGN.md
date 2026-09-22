# Production Design Notes (≤400 words)

## Deployment

Ship the two services as separate containers behind an internal network,
mirroring the trust boundary already in the code: Spring's public API is
the only ingress, behind a load balancer / API gateway (TLS termination,
rate limiting, real authentication in place of the static `X-Caller-Id`
registry). Python is never exposed outside the cluster network. Each
service scales independently; Python is CPU-bound on PDF parsing so it
benefits from more replicas under batch load, while Spring is mostly
I/O-bound waiting on Python. A shared config/secrets store (not env vars
baked into images) should hold the caller registry once it grows past 3
static entries and needs real provisioning.

## Top security risks

1. **PII in transit and at rest.** Reimbursement documents contain names,
   employee IDs, and amounts. Today nothing persists them, but production
   will need encryption in transit (mTLS between services), encryption at
   rest for any stored documents, and a retention/deletion policy — this
   is the biggest gap relative to a real deployment.
2. **Prompt injection from policy text and documents.** Structurally
   mitigated here (eligibility never reads record/document text, generation
   never interprets text as instructions), but if a real LLM ever replaces
   the deterministic double, this boundary must be re-verified — an LLM
   that reads policy text is exactly the thing an embedded "SYSTEM MESSAGE"
   is designed to compromise.
3. **Caller identity spoofing.** `X-Caller-Id` is trusted as-is with no
   signature or session validation. Production needs real authentication
   (OAuth/JWT) so the header can't simply be forged by anyone who can reach
   the Spring service.

## Operational risks

The "slow/partially-documented approval system" referenced in the source
material is a backpressure risk if it's ever wired in synchronously:
Spring's bounded-timeout/no-retry contract with Python protects the request
path, but a downstream approval system without similar timeout discipline
could stall batch processing or silently drop review outcomes. It should be
integrated asynchronously (queue + webhook/poll) rather than in the request
path, with its own circuit breaker.

## What to clarify before committing to a delivery date

- Expected batch size/throughput (documents per batch, batches per hour) —
  determines whether per-document Python calls should be parallelized.
- Whether the approval system is a hard dependency for `review_required`
  items, and its actual latency/SLA.
- Real authentication mechanism and caller provisioning process (who adds
  new tenants/roles, and how often).
