import json
from pathlib import Path

from app.retrieval.topics import detect_topic

_POLICIES_PATH = Path(__file__).resolve().parent.parent / "data" / "policies.json"


def load_policies() -> list[dict]:
    """Load the policy corpus, tagging each record with its topic.

    Tagging is done once here with the same keyword tagger used for
    questions, so record order/duplication/additions in policies.json never
    require code changes.
    """
    with open(_POLICIES_PATH, encoding="utf-8") as f:
        records = json.load(f)
    for record in records:
        record["topic"] = detect_topic(record["text"])
    return records
