"""Deterministic, keyword-based topic tagging.

This is intentionally simple: a small fixed vocabulary maps free text to one
of a handful of benefit topics. It is not robust to heavy paraphrase (see
README "out of scope" section) but it is fully offline, auditable, and never
treats the input text as anything other than data to scan for keywords.
"""

TOPIC_KEYWORDS: dict[str, tuple[str, ...]] = {
    "certification": ("certificat", "cert exam", "cert fee"),
    "home-office": ("home office", "home-office", "wfh", "remote setup", "equipment allowance"),
    "training": ("training", "course", "workshop", "seminar"),
    "travel": ("travel", "business trip", "flight", "hotel"),
}


def detect_topic(text: str | None) -> str | None:
    """Return the first matching topic for the given text, or None."""
    if not text:
        return None
    lowered = text.lower()
    for topic, keywords in TOPIC_KEYWORDS.items():
        if any(keyword in lowered for keyword in keywords):
            return topic
    return None
