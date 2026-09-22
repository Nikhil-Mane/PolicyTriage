package com.marlabs.support.caller;

import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Static in-memory registry of the 3 supplied callers. Spring is the sole
 * source of truth for tenant/role: Python never re-derives identity from
 * document or policy text.
 */
@Component
public class CallerRegistry {

    private static final Map<String, CallerIdentity> CALLERS = Map.of(
            "atlas-employee-01", new CallerIdentity("atlas", "employee"),
            "atlas-contractor-01", new CallerIdentity("atlas", "contractor"),
            "boreal-employee-01", new CallerIdentity("boreal", "employee")
    );

    public Optional<CallerIdentity> lookup(String callerId) {
        if (callerId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(CALLERS.get(callerId));
    }
}
