#!/usr/bin/env python3
"""
Karışık TEXT + OCR queue yük testi (HMAC imzalı)

Zorunlu env:
  APP_SIGNING_SECRET=<backend ile aynı secret>

Opsiyonel env:
  BASE_URL=http://localhost:8080
  SIGNING_PATH_PREFIX=
  TEXT_USERS=30
  TEXT_REQUESTS_PER_USER=1
  OCR_USERS=10
  OCR_REQUESTS_PER_USER=1
  POLL_INTERVAL_SEC=4
  MAX_WAIT_SEC=1800
  OCR_IMAGE_PATH=sample-petition.png
"""

from __future__ import annotations

import base64
import hashlib
import hmac
import json
import mimetypes
import os
import random
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from statistics import mean, median
from urllib import error, request

BASE_URL = os.getenv("BASE_URL", "").rstrip("/")
APP_SIGNING_SECRET = os.getenv("APP_SIGNING_SECRET", "")
SIGNING_PATH_PREFIX = os.getenv("SIGNING_PATH_PREFIX", "").rstrip("/")

GENERATE_PATH = "/api/v1/petition/generate"
TEXT_STATUS_PATH_FMT = "/api/v1/petition/status/{ticket_id}"
OCR_QUEUE_PATH = "/api/v1/petition/ocr-layout/queue"
OCR_STATUS_PATH_FMT = "/api/v1/petition/ocr-layout/status/{ticket_id}"

TEXT_USERS = int(os.getenv("TEXT_USERS", "30"))
TEXT_REQUESTS_PER_USER = int(os.getenv("TEXT_REQUESTS_PER_USER", "1"))
OCR_USERS = int(os.getenv("OCR_USERS", "10"))
OCR_REQUESTS_PER_USER = int(os.getenv("OCR_REQUESTS_PER_USER", "1"))

POLL_INTERVAL_SEC = float(os.getenv("POLL_INTERVAL_SEC", "4.0"))
MAX_WAIT_SEC = float(os.getenv("MAX_WAIT_SEC", "1800"))
PROGRESS_EVERY_SEC = float(os.getenv("PROGRESS_EVERY_SEC", "10"))
OCR_IMAGE_PATH = os.getenv("OCR_IMAGE_PATH", "sample-petition.png")

PRINT_LOCK = threading.Lock()
STATE_LOCK = threading.Lock()
CURRENT_STATES = {}

TEXT_PROMPTS = [
    "Üniversitede devamsızlık affı için resmi dilekçe taslağı oluştur.",
    "Sınav tarih çakışması nedeniyle mazeret sınavı talep eden resmi dilekçe hazırla.",
    "Staj başlangıç tarihinin güncellenmesi için resmi bir dilekçe hazırla.",
    "Harç iadesi talebi için resmi dilekçe oluştur.",
]


def log(msg: str) -> None:
    with PRINT_LOCK:
        print(msg, flush=True)


def set_state(job_key: str, state: str) -> None:
    with STATE_LOCK:
        CURRENT_STATES[job_key] = state


def snapshot_states() -> dict:
    with STATE_LOCK:
        values = list(CURRENT_STATES.values())

    counts = {k: 0 for k in ["SUBMITTED", "QUEUED", "PROCESSING", "COMPLETED", "FAILED", "HTTP_ERROR", "NOT_FOUND", "TIMEOUT", "OTHER"]}
    for v in values:
        counts[v if v in counts else "OTHER"] += 1
    return counts


def now_epoch() -> int:
    return int(time.time())


def build_signing_path(path: str) -> str:
    return f"{SIGNING_PATH_PREFIX}{path}" if SIGNING_PATH_PREFIX else path


def sign_request(method: str, path: str, timestamp: int, client_id: str) -> str:
    payload = f"{method}\n{build_signing_path(path)}\n{timestamp}\n{client_id}"
    return hmac.new(APP_SIGNING_SECRET.encode("utf-8"), payload.encode("utf-8"), hashlib.sha256).hexdigest()


def http_json(method: str, path: str, client_id: str, data=None, timeout=120):
    body = None if data is None else json.dumps(data, ensure_ascii=False).encode("utf-8")
    ts = now_epoch()
    sig = sign_request(method.upper(), path, ts, client_id)

    req = request.Request(f"{BASE_URL}{path}", data=body, method=method.upper())
    req.add_header("Accept", "application/json")
    req.add_header("X-Client-Id", client_id)
    req.add_header("X-Timestamp", str(ts))
    req.add_header("X-Signature", sig)
    if body is not None:
        req.add_header("Content-Type", "application/json; charset=utf-8")

    try:
        with request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8", errors="replace")
            return resp.status, json.loads(raw) if raw.strip() else {}
    except error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        try:
            parsed = json.loads(raw) if raw.strip() else {}
        except Exception:
            parsed = {"raw": raw}
        return e.code, parsed
    except Exception as e:
        return 0, {"error": str(e)}


def percentile(sorted_values, p):
    if not sorted_values:
        return None
    if len(sorted_values) == 1:
        return sorted_values[0]
    k = (len(sorted_values) - 1) * p
    f = int(k)
    c = min(f + 1, len(sorted_values) - 1)
    if f == c:
        return sorted_values[f]
    return round(sorted_values[f] * (c - k) + sorted_values[c] * (k - f), 2)


def load_ocr_payload():
    if not os.path.exists(OCR_IMAGE_PATH):
        raise FileNotFoundError(f"OCR dosyası bulunamadı: {OCR_IMAGE_PATH}")
    mime_type, _ = mimetypes.guess_type(OCR_IMAGE_PATH)
    if not mime_type:
        mime_type = "image/png"
    with open(OCR_IMAGE_PATH, "rb") as f:
        image_b64 = base64.b64encode(f.read()).decode("utf-8")
    return {"imageBase64": image_b64, "mimeType": mime_type}


def wait_for_terminal_status(status_fn, job_key: str, ticket_id: str):
    deadline = time.time() + MAX_WAIT_SEC
    while time.time() < deadline:
        http_code, payload = status_fn(ticket_id)
        if http_code == 404:
            set_state(job_key, "NOT_FOUND")
            return "NOT_FOUND", http_code, payload
        if http_code != 200:
            set_state(job_key, "HTTP_ERROR")
            return "HTTP_ERROR", http_code, payload

        current = payload.get("status")
        if current:
            set_state(job_key, current)
        if current in {"COMPLETED", "FAILED"}:
            return current, http_code, payload
        time.sleep(max(1.0, POLL_INTERVAL_SEC + random.uniform(-0.5, 0.5)))

    set_state(job_key, "TIMEOUT")
    return "TIMEOUT", None, None


def run_text_job(client_id: str, request_no: int, prompt: str, start_event):
    job_key = f"TEXT:{client_id}:{request_no}"
    start_event.wait()
    t0 = time.time()
    set_state(job_key, "SUBMITTED")

    code, data = http_json("POST", GENERATE_PATH, client_id, {"text": prompt})
    if code != 200:
        set_state(job_key, "HTTP_ERROR")
        return {"kind": "TEXT", "clientId": client_id, "requestNo": request_no, "finalStatus": "HTTP_ERROR", "httpStatus": code, "ticketId": None, "doneSec": round(time.time() - t0, 2), "raw": data}

    ticket_id = data.get("ticketId")
    if not ticket_id:
        return {"kind": "TEXT", "clientId": client_id, "requestNo": request_no, "finalStatus": data.get("status") or "NO_TICKET", "httpStatus": code, "ticketId": None, "doneSec": round(time.time() - t0, 2), "raw": data}

    def status_fn(tid):
        return http_json("GET", TEXT_STATUS_PATH_FMT.format(ticket_id=tid), client_id)

    final_status, status_code, payload = wait_for_terminal_status(status_fn, job_key, ticket_id)
    return {"kind": "TEXT", "clientId": client_id, "requestNo": request_no, "finalStatus": final_status, "httpStatus": status_code, "ticketId": ticket_id, "doneSec": round(time.time() - t0, 2), "raw": payload}


def run_ocr_queue_job(user_no: int, req_no: int, ocr_payload: dict, start_event):
    client_id = f"ocr-user-{user_no}"
    job_key = f"OCR:{client_id}:{req_no}"
    start_event.wait()
    t0 = time.time()
    set_state(job_key, "SUBMITTED")

    code, data = http_json("POST", OCR_QUEUE_PATH, client_id, ocr_payload)
    if code != 200:
        set_state(job_key, "HTTP_ERROR")
        return {"kind": "OCR", "userNo": user_no, "requestNo": req_no, "finalStatus": "HTTP_ERROR", "httpStatus": code, "ticketId": None, "doneSec": round(time.time() - t0, 2), "raw": data}

    ticket_id = data.get("ticketId")
    if not ticket_id:
        return {"kind": "OCR", "userNo": user_no, "requestNo": req_no, "finalStatus": data.get("status") or "NO_TICKET", "httpStatus": code, "ticketId": None, "doneSec": round(time.time() - t0, 2), "raw": data}

    def status_fn(tid):
        return http_json("GET", OCR_STATUS_PATH_FMT.format(ticket_id=tid), client_id)

    final_status, status_code, payload = wait_for_terminal_status(status_fn, job_key, ticket_id)
    return {"kind": "OCR", "userNo": user_no, "requestNo": req_no, "finalStatus": final_status, "httpStatus": status_code, "ticketId": ticket_id, "doneSec": round(time.time() - t0, 2), "raw": payload}


def progress_reporter(stop_event: threading.Event, total_jobs: int):
    start = time.time()
    while not stop_event.is_set():
        time.sleep(PROGRESS_EVERY_SEC)
        counts = snapshot_states()
        done = counts["COMPLETED"] + counts["FAILED"] + counts["HTTP_ERROR"] + counts["NOT_FOUND"] + counts["TIMEOUT"]
        log(f"[PROGRESS t={round(time.time()-start,1)}s] submitted={counts['SUBMITTED']} queued={counts['QUEUED']} processing={counts['PROCESSING']} completed={counts['COMPLETED']} failed={counts['FAILED']} done={done}/{total_jobs}")


def summarize(title: str, results: list, owner_key: str):
    print("\n" + "=" * 90)
    print(title)
    print("=" * 90)
    for i, r in enumerate(sorted(results, key=lambda x: x["doneSec"]), 1):
        print(f"{i:03d}. {owner_key}={r.get(owner_key)} final={r['finalStatus']:<10} done={r['doneSec']:<8}s ticket={r.get('ticketId')}")

    completed = [r["doneSec"] for r in results if r["finalStatus"] == "COMPLETED"]
    if completed:
        completed.sort()
        print("-" * 90)
        print(f"Ortalama: {round(mean(completed),2)} s | Medyan: {round(median(completed),2)} s | P95: {percentile(completed,0.95)} s")

    failed = [r for r in results if r["finalStatus"] != "COMPLETED"]
    if failed:
        print("Problemli işler:")
        for f in failed:
            print(f"- owner={f.get(owner_key)} final={f['finalStatus']} http={f.get('httpStatus')} ticket={f.get('ticketId')}")


def main():
    if not APP_SIGNING_SECRET:
        print("HATA: APP_SIGNING_SECRET zorunlu", file=sys.stderr)
        return 2

    total_text = TEXT_USERS * TEXT_REQUESTS_PER_USER
    total_ocr = OCR_USERS * OCR_REQUESTS_PER_USER
    total_jobs = total_text + total_ocr

    log(f"BASE_URL={BASE_URL}")
    log(f"SIGNING_PATH_PREFIX={SIGNING_PATH_PREFIX!r}")
    log(f"TEXT_USERS={TEXT_USERS} x {TEXT_REQUESTS_PER_USER}")
    log(f"OCR_USERS={OCR_USERS} x {OCR_REQUESTS_PER_USER}")

    ocr_payload = load_ocr_payload() if total_ocr > 0 else None

    start_event = threading.Event()
    stop_event = threading.Event()
    reporter = threading.Thread(target=progress_reporter, args=(stop_event, total_jobs), daemon=True)
    reporter.start()

    futures = []
    results = []
    max_workers = max(16, min(total_jobs + 8, 256))

    try:
        with ThreadPoolExecutor(max_workers=max_workers) as ex:
            for user_no in range(1, TEXT_USERS + 1):
                client_id = f"fake-user-{user_no}"
                for req_no in range(1, TEXT_REQUESTS_PER_USER + 1):
                    prompt = TEXT_PROMPTS[(user_no + req_no - 2) % len(TEXT_PROMPTS)]
                    futures.append(ex.submit(run_text_job, client_id, req_no, prompt, start_event))

            for user_no in range(1, OCR_USERS + 1):
                for req_no in range(1, OCR_REQUESTS_PER_USER + 1):
                    futures.append(ex.submit(run_ocr_queue_job, user_no, req_no, ocr_payload, start_event))

            log("[START] HMAC imzalı TEXT+OCR queue yük testi başlıyor")
            start_event.set()

            for fut in as_completed(futures):
                results.append(fut.result())
    finally:
        stop_event.set()

    text_results = [r for r in results if r["kind"] == "TEXT"]
    ocr_results = [r for r in results if r["kind"] == "OCR"]
    summarize("TEXT SONUÇLARI", text_results, "clientId")
    summarize("OCR SONUÇLARI", ocr_results, "userNo")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print("\nTest iptal edildi.")
        sys.exit(1)