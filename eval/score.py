#!/usr/bin/env python3
"""Score retrieval hit-rate against a running Smart Management API.

Stdlib only. Rows with a null expected_source are printed and not scored.
"""

import argparse
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path


def hit(expected, names):
    if not expected:
        return None
    expected_name = expected.casefold()
    return any(str(name).casefold() == expected_name for name in names)


def self_test():
    assert hit("Refund Policy.md", ["Refund Policy.md", "Other.md"]) is True
    assert hit("Account Closure Terms.md", ["Refund Policy.md"]) is False
    assert hit(None, ["Refund Policy.md"]) is None
    print("self-test ok")


def request_json(url, method="GET", payload=None, token=None):
    data = None if payload is None else json.dumps(payload).encode()
    headers = {"Accept": "application/json"}
    if payload is not None:
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=30) as response:
        return json.load(response)


def login(base, email, password):
    body = request_json(
        base.rstrip("/") + "/api/auth/login",
        method="POST",
        payload={"email": email, "password": password},
    )
    return body["accessToken"]


def search(base, token, question, mode, k):
    query = urllib.parse.urlencode({"q": question, "mode": mode, "size": k})
    body = request_json(base.rstrip("/") + "/api/search?" + query, token=token)
    return [hit_row.get("documentName") for hit_row in body.get("hits", [])]


def score(dataset, base, token, mode, k):
    scored = 0
    hits = 0
    for row in dataset:
        expected = row.get("expected_source")
        names = search(base, token, row["question"], mode, k)
        outcome = hit(expected, names)
        if outcome is None:
            print(f"SKIP  {row['id']}: no expected source")
            continue
        scored += 1
        hits += int(outcome)
        mark = "HIT " if outcome else "MISS"
        print(f"{mark}  {row['id']}: expected {expected}; got {names}")
    rate = (hits / scored) if scored else 0
    print(f"\nhit-rate {hits}/{scored} = {rate:.0%}  mode={mode} k={k}")
    return rate


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--dataset", default=str(Path(__file__).with_name("dataset.jsonl")))
    parser.add_argument("--base", default="http://localhost:8080")
    parser.add_argument("--email", default="manager@local")
    parser.add_argument("--password", default="manager123")
    parser.add_argument("--mode", default="HYBRID", choices=["KEYWORD", "SEMANTIC", "HYBRID"])
    parser.add_argument("--k", type=int, default=5)
    args = parser.parse_args(argv)
    if args.self_test:
        self_test()
        return 0
    rows = []
    with open(args.dataset, encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if line:
                rows.append(json.loads(line))
    token = login(args.base, args.email, args.password)
    score(rows, args.base, token, args.mode, args.k)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv[1:]))
    except urllib.error.URLError as exc:
        print("API request failed:", exc, file=sys.stderr)
        sys.exit(1)
