from pydantic import BaseModel


class AnswerRequest(BaseModel):
    tenant: str
    role: str
    as_of: str
    question: str


class Citation(BaseModel):
    chunk_id: str
    quote: str


class AnswerResponse(BaseModel):
    status: str
    answer: str | None
    citations: list[Citation]
