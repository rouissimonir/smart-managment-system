#!/usr/bin/env python3
"""Exercise a running demo stack with the offline embedding client.

Stdlib only. Expects docker compose --profile demo to be up, with seeded
policy documents and no OPENAI_API_KEY.
"""

import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request


API = "http://localhost:8080"
UI = "http://localhost:8088"
PHRASE = "zephyr quartz invoice 4821"


def request(url, method="GET", payload=None, token=None, raw=None, content_type=None, timeout=30, parse_json=True):
    data = raw
    headers = {"Accept": "application/json"}
    if payload is not None:
        data = json.dumps(payload).encode()
        headers["Content-Type"] = "application/json"
    elif content_type:
        headers["Content-Type"] = content_type
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            body = response.read()
            if not parse_json:
                return response.status, body
            return response.status, json.loads(body) if body else None
    except urllib.error.HTTPError as exc:
        body = exc.read()
        parsed = None
        if body:
            try:
                parsed = json.loads(body)
            except json.JSONDecodeError:
                parsed = body.decode(errors="replace")
        return exc.code, parsed
    except (urllib.error.URLError, TimeoutError, ConnectionError, OSError) as exc:
        return 0, str(exc)


def wait_until(label, predicate, seconds=180):
    deadline = time.time() + seconds
    last = None
    while time.time() < deadline:
        ok, last = predicate()
        if ok:
            return last
        time.sleep(3)
    raise SystemExit(f"Timed out waiting for {label}: {last}")


def login(email, password):
    status, body = request(
        API + "/api/auth/login",
        method="POST",
        payload={"email": email, "password": password},
    )
    if status != 200 or not isinstance(body, dict) or "accessToken" not in body:
        raise SystemExit(f"Login failed for {email}: {status} {body}")
    return body["accessToken"]


def main():
    def health():
        status, body = request(API + "/api/health")
        return status == 200 and isinstance(body, dict) and body.get("status") == "UP", body

    wait_until("API health", health)

    status, ui = request(UI + "/", parse_json=False)
    if status != 200 or b"<app-root" not in ui:
        raise SystemExit(f"Frontend did not serve the app: {status}")
    status, proxied = request(UI + "/api/health")
    if status != 200 or not isinstance(proxied, dict) or proxied.get("status") != "UP":
        raise SystemExit(f"Frontend did not proxy /api/health: {status} {proxied}")

    manager = login("manager@local", "manager123")
    status, projects = request(API + "/api/projects?size=50", token=manager)
    if status != 200:
        raise SystemExit(f"Project list failed: {status} {projects}")
    by_name = {row["name"]: row for row in projects.get("content", [])}
    for required in ("Website revamp", "Mobile rollout"):
        if required not in by_name:
            raise SystemExit(f"Seed project missing: {required}; saw {list(by_name)}")
    website_id = by_name["Website revamp"]["id"]

    member = login("user@local", "user123")
    status, visible = request(API + "/api/projects?size=50", token=member)
    if status != 200:
        raise SystemExit(f"USER project list failed: {status} {visible}")
    names = {row["name"] for row in visible.get("content", [])}
    if "Mobile rollout" not in names or "Website revamp" in names:
        raise SystemExit(f"USER visibility is wrong: {names}")
    status, denied = request(API + "/api/users", token=member)
    if status != 403:
        raise SystemExit(f"USER should not list users, got {status}")

    def documents_ready():
        status, docs = request(API + f"/api/projects/{website_id}/documents", token=manager)
        if status != 200 or not isinstance(docs, list):
            return False, docs
        wanted = [d for d in docs if d["name"] in ("Refund Policy.md", "Account Closure Terms.md")]
        ready = len(wanted) == 2 and all(d["status"] == "READY" and d["chunkCount"] > 0 for d in wanted)
        return ready, [(d["name"], d["status"], d.get("errorMessage")) for d in docs]

    wait_until("seeded documents", documents_ready, seconds=180)

    query = urllib.parse.urlencode({"q": "refund within 30 days", "mode": "KEYWORD", "size": 5})
    status, hits = request(API + "/api/search?" + query, token=manager)
    if status != 200:
        raise SystemExit(f"Search failed: {status} {hits}")
    found = [hit["documentName"] for hit in hits.get("hits", [])]
    if "Refund Policy.md" not in found:
        raise SystemExit(f"Keyword search missed Refund Policy.md: {found}")

    status, chat = request(
        API + "/api/chat",
        method="POST",
        token=manager,
        payload={"message": "How long do customers have to request a refund?"},
    )
    if status != 200 or not isinstance(chat, dict):
        raise SystemExit(f"Chat failed: {status} {chat}")
    sources = [source.get("name") for source in chat.get("sources") or []]
    if "Refund Policy.md" not in sources:
        raise SystemExit(f"Chat did not cite Refund Policy.md: {chat}")

    boundary = "----demo-smoke"
    file_body = f"The {PHRASE} is due on receipt.\n".encode()
    preamble = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="Zephyr Note.md"\r\n'
        f"Content-Type: text/markdown\r\n\r\n"
    ).encode()
    closing = f"\r\n--{boundary}--\r\n".encode()
    status, uploaded = request(
        API + "/api/documents?projectId=" + website_id,
        method="POST",
        token=manager,
        raw=preamble + file_body + closing,
        content_type=f"multipart/form-data; boundary={boundary}",
    )
    if status not in (200, 202) or not isinstance(uploaded, dict):
        raise SystemExit(f"Upload failed: {status} {uploaded}")
    uploaded_id = uploaded["id"]

    def upload_ready():
        status, doc = request(API + f"/api/documents/{uploaded_id}", token=manager)
        if status != 200 or not isinstance(doc, dict):
            return False, doc
        if doc["status"] == "FAILED":
            raise SystemExit(f"Uploaded note failed ingestion: {doc.get('errorMessage')}")
        return doc["status"] == "READY" and doc["chunkCount"] > 0, doc.get("status")

    wait_until("uploaded note", upload_ready, seconds=180)

    query = urllib.parse.urlencode({"q": PHRASE, "mode": "KEYWORD", "size": 5})
    status, hits = request(API + "/api/search?" + query, token=manager)
    found = [hit["documentName"] for hit in (hits or {}).get("hits", [])] if status == 200 else []
    if "Zephyr Note.md" not in found:
        raise SystemExit(f"Uploaded note was not searchable: {status} {found}")

    print("demo smoke ok")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except urllib.error.URLError as exc:
        print("API request failed:", exc, file=sys.stderr)
        sys.exit(1)
