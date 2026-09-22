"""Exceptions representing failure modes of the (simulated) generation model.

The generation engine itself is a deterministic offline double and never
raises these in production. They exist as the documented failure contract
between the generation step and its caller, exercised in tests via a
controllable fake engine so that /internal/process-document's failure
handling (single attempt, no retry, stable error code) can be verified
without a real model dependency.
"""


class ModelTimeoutError(Exception):
    pass


class ModelUnavailableError(Exception):
    pass


class MalformedModelOutputError(Exception):
    pass
