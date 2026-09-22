"""Raw text extraction from uploaded request documents (TXT / PDF only)."""
import io

from pypdf import PdfReader
from pypdf.errors import PdfReadError


class EmptyFileError(Exception):
    pass


class UnreadableFileError(Exception):
    pass


def extract_text(filename: str, content: bytes) -> str:
    if not content:
        raise EmptyFileError(f"'{filename}' is empty")

    if filename.lower().endswith(".pdf"):
        try:
            reader = PdfReader(io.BytesIO(content))
            pages = [page.extract_text() or "" for page in reader.pages]
            text = "\n".join(pages).strip()
        except PdfReadError as exc:
            raise UnreadableFileError(f"'{filename}' could not be parsed as PDF") from exc
        if not text:
            raise UnreadableFileError(f"'{filename}' contained no extractable text")
        return text

    try:
        text = content.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise UnreadableFileError(f"'{filename}' is not valid UTF-8 text") from exc
    return text
