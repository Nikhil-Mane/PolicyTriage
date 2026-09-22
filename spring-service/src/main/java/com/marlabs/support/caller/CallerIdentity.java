package com.marlabs.support.caller;

/** Tenant/role resolved from a caller's {@code X-Caller-Id} header. */
public record CallerIdentity(String tenant, String role) {
}
