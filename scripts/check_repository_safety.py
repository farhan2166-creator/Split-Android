#!/usr/bin/env python3
"""Fail CI if a tracked file looks like private user data or a credential.

This is a safety net, not a complete secret-scanning replacement. Git history,
GitHub Releases, and GitHub repository settings require separate review.
"""
from __future__ import annotations

import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
FILE_LIMIT = 2_000_000  # Scan source/config, not large binary assets.

CREDENTIAL_PATTERNS: tuple[tuple[str, re.Pattern[bytes]], ...] = (
    ("PEM private key", re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----")),
    ("GitHub access token", re.compile(rb"(?:gh[pousr]_[A-Za-z0-9_]{20,}|github_pat_[A-Za-z0-9_]{20,})")),
    ("AWS access key", re.compile(rb"AKIA[0-9A-Z]{16}")),
    ("Google API key", re.compile(rb"AIza[A-Za-z0-9_-]{30,}")),
    ("OpenAI API key", re.compile(rb"sk-(?:proj-)?[A-Za-z0-9_-]{25,}")),
    ("large embedded base64 string", re.compile(rb"(?<![A-Za-z0-9+/])[A-Za-z0-9+/]{650,}={0,2}(?![A-Za-z0-9+/])")),
)

FORBIDDEN_SUFFIXES = (
    ".jks", ".keystore", ".p12", ".pfx", ".pem", ".key",
    ".db", ".sqlite", ".sqlite3", ".env",
)


def private_filename(path: str) -> bool:
    lower = path.lower().replace("\\", "/")
    name = lower.rsplit("/", 1)[-1]
    if name.endswith(FORBIDDEN_SUFFIXES) or name.startswith(".env."):
        return True
    if name in {"signing.properties", "local.properties", "google-services.json",
                "service-account.json", "credentials.json", "passwords.txt"}:
        return True
    if ("credential" in name or "secret" in name) and name.endswith((".txt", ".json", ".properties")):
        return True
    if (("backup" in name or "ledger" in name or "export" in name)
            and name.endswith((".json", ".csv", ".pdf"))):
        return True
    return False


def inspect_path(path: str) -> list[str]:
    issues = []
    if private_filename(path):
        issues.append("private-looking filename")
        return issues
    full = ROOT / path
    if not full.is_file():
        return issues
    if full.stat().st_size > FILE_LIMIT:
        # A large binary is not presumed safe; it needs a review.
        issues.append("tracked file exceeds 2 MB safety-review threshold")
        return issues
    raw = full.read_bytes()
    if b"\x00" in raw:
        return issues
    for label, pattern in CREDENTIAL_PATTERNS:
        if pattern.search(raw):
            issues.append(label)
    return issues


def main() -> int:
    paths = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT).decode().split("\0")
    problems: list[tuple[str, str]] = []
    for path in filter(None, paths):
        for issue in inspect_path(path):
            problems.append((path, issue))
    if problems:
        print("Repository safety check failed:")
        for path, issue in problems:
            print(f"  {path}: {issue}")
        print("Remove credentials from Git history and rotate exposed secrets before publishing.")
        return 1
    print(f"Repository safety check passed ({sum(bool(p) for p in paths)} tracked files).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
