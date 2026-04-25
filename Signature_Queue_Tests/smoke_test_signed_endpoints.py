#!/usr/bin/env python3
"""
Signed endpoint smoke test for both Text AI and OCR flows.

Usage:
  APP_SIGNING_SECRET=... python scripts/smoke_test_signed_endpoints.py

Optional env vars:
  BASE_URL=http://localhost:8080
  CLIENT_ID=test-client-1
  POLL_ATTEMPTS=20
  POLL_INTERVAL_SECONDS=2
"""

from __future__ import annotations

import base64
import hashlib
import hmac
import json
import os
import sys
import time
import uuid
from typing import Any, Dict, Optional, Tuple
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


BASE_URL = os.getenv("BASE_URL", "").rstrip("/")
APP_SIGNING_SECRET = os.getenv("APP_SIGNING_SECRET", "")
CLIENT_ID = os.getenv("CLIENT_ID", f"smoke-{uuid.uuid4()}")
POLL_ATTEMPTS = int(os.getenv("POLL_ATTEMPTS", "20"))
POLL_INTERVAL_SECONDS = float(os.getenv("POLL_INTERVAL_SECONDS", "2"))

# 1x1 transparent PNG (valid base64)
TINY_PNG_BASE64 = (
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR4nGNgYAAAAAMAASsJTYQAAAAASUVORK5CYII="
)


def now_epoch_seconds() -> int:
    return int(time.time())


def sign(method: str, path: str, timestamp: int, client_id: str, secret: str) -> str:
    payload = f"{method}\n{path}\n{timestamp}\n{client_id}"
    digest = hmac.new(secret.encode("utf-8"), payload.encode("utf-8"), hashlib.sha256).hexdigest()
    return digest


def request_json(method: str, path: str, body: Optional[Dict[str, Any]] = None) -> Tuple[int, Dict[str, Any]]:
    timestamp = now_epoch_seconds()
    signature = sign(method.upper(), path, timestamp, CLIENT_ID, APP_SIGNING_SECRET)

    headers = {
        "Content-Type": "application/json",
        "X-Client-Id": CLIENT_ID,
        "X-Timestamp": str(timestamp),
        "X-Signature": signature,
    }

    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")

    req = Request(url=f"{BASE_URL}{path}", data=data, method=method.upper(), headers=headers)

    try:
        with urlopen(req, timeout=30) as resp:
            raw = resp.read().decode("utf-8")
            return resp.status, json.loads(raw) if raw else {}
    except HTTPError as e:
        raw = e.read().decode("utf-8") if e.fp else ""
        parsed = {}
        if raw:
            try:
                parsed = json.loads(raw)
            except json.JSONDecodeError:
                parsed = {"raw": raw}
        return e.code, parsed
    except URLError as e:
        raise RuntimeError(f"Server erişimi başarısız: {e}") from e


def poll_status(path_template: str, ticket_id: str) -> Dict[str, Any]:
    path = path_template.format(ticket_id=ticket_id)
    last = {}
    for _ in range(POLL_ATTEMPTS):
        code, payload = request_json("GET", path)
        if code != 200:
            raise RuntimeError(f"Status endpoint HTTP {code}: {payload}")

        status = payload.get("status")
        last = payload
        if status in {"COMPLETED", "FAILED"}:
            return payload
        time.sleep(POLL_INTERVAL_SECONDS)

    raise RuntimeError(f"Status timeout. Son durum: {last}")


def test_text_flow() -> None:
    print("\n[TEXT] /generate akışı test ediliyor...")

    code, payload = request_json("POST", "/api/v1/petition/generate", {"text": "Burs başvurusu için resmi dilekçe taslağı oluştur."})
    if code != 200:
        raise RuntimeError(f"Text generate HTTP {code}: {payload}")

    if payload.get("status") not in {"PROCESSING", "QUEUED"}:
        raise RuntimeError(f"Beklenmeyen text başlangıç durumu: {payload}")

    ticket_id = payload.get("ticketId")
    if not ticket_id:
        raise RuntimeError(f"Text ticketId boş: {payload}")

    final_state = poll_status("/api/v1/petition/status/{ticket_id}", ticket_id)
    print(f"[TEXT] Final durum: {final_state.get('status')}")
    if final_state.get("status") == "FAILED":
        raise RuntimeError(f"Text FAILED payload: {final_state.get('payload')}")


def test_ocr_flow() -> None:
    print("\n[OCR] /ocr-layout/queue akışı test ediliyor...")

    body = {
        "imageBase64": TINY_PNG_BASE64,
        "mimeType": "image/png",
    }
    code, payload = request_json("POST", "/api/v1/petition/ocr-layout/queue", body)
    if code != 200:
        raise RuntimeError(f"OCR queue HTTP {code}: {payload}")

    if payload.get("status") not in {"QUEUED", "PROCESSING"}:
        raise RuntimeError(f"Beklenmeyen OCR başlangıç durumu: {payload}")

    ticket_id = payload.get("ticketId")
    if not ticket_id:
        raise RuntimeError(f"OCR ticketId boş: {payload}")

    final_state = poll_status("/api/v1/petition/ocr-layout/status/{ticket_id}", ticket_id)
    print(f"[OCR] Final durum: {final_state.get('status')}")
    if final_state.get("status") == "FAILED":
        raise RuntimeError(f"OCR FAILED error: {final_state.get('errorMessage') or final_state.get('payload')}")


def main() -> int:
    if not APP_SIGNING_SECRET:
        print("HATA: APP_SIGNING_SECRET environment variable zorunlu.", file=sys.stderr)
        return 2

    print("Signed smoke test başlıyor")
    print(f"BASE_URL={BASE_URL}")
    print(f"CLIENT_ID={CLIENT_ID}")
    print(f"POLL_ATTEMPTS={POLL_ATTEMPTS}, POLL_INTERVAL_SECONDS={POLL_INTERVAL_SECONDS}")

    test_text_flow()
    test_ocr_flow()

    print("\n✅ Tüm signed smoke testleri geçti (TEXT + OCR).")
    return 0


if __name__ == "__main__":
    sys.exit(main())