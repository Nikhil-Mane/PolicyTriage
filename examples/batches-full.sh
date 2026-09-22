#!/usr/bin/env bash
# Full 8-document batch, including the PDF, a byte-duplicate, and a zero-byte file.
#
# Note: cd into this directory first so the paths passed to curl are
# relative -- some curl builds mis-parse the "@path;type=..." form when the
# path contains spaces, which the absolute path to this repo does.
cd "$(dirname "${BASH_SOURCE[0]}")"

curl -sS -X POST http://localhost:8080/batches \
  -H "X-Caller-Id: atlas-employee-01" \
  -F "metadata=@manifest.json;type=application/json" \
  -F "files=@../fixtures/requests/request-01.txt;type=text/plain" \
  -F "files=@../fixtures/requests/request-02.pdf;type=application/pdf" \
  -F "files=@../fixtures/requests/request-03.txt;type=text/plain" \
  -F "files=@../fixtures/requests/request-04.txt;type=text/plain" \
  -F "files=@../fixtures/requests/request-05.txt;type=text/plain" \
  -F "files=@../fixtures/requests/request-06.txt;type=text/plain" \
  -F "files=@../fixtures/requests/request-07.txt;type=text/plain" \
  -F "files=@../fixtures/requests/request-08.txt;type=text/plain"
