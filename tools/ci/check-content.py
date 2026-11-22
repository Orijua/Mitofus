#!/usr/bin/env python3
"""Fails (exit 1) if a tracked file (path or content) matches any rule.

Rules are regular expressions, one per line (blank lines and lines starting with '#' are ignored), read from the
environment variable named by --env (default CONTENT_RULES) or from a file given with --rules-file.

The output only has `path:line` (or `path:name` for a path match). It never prints a rule or the matching text,
so the logs of a public repository reveal nothing about the rules.
Lines that contain the exact text 'content-check: ignore' are skipped.
Exit codes: 0 = clean, 1 = matches found, 2 = no rules available (fails closed).
"""
import argparse, gzip, os, re, subprocess, sys
from pathlib import Path

ap = argparse.ArgumentParser()
ap.add_argument('--root', default='.')
ap.add_argument('--env', default='CONTENT_RULES')
ap.add_argument('--rules-file', default=None)
a = ap.parse_args()

root = Path(a.root).resolve()
source = Path(a.rules_file).read_text(encoding='utf-8') if a.rules_file else os.environ.get(a.env, '')
pats = []
for line in source.splitlines():
    line = line.strip()
    if line and not line.startswith('#'):
        try:
            pats.append(re.compile(line, re.I))
        except re.error:
            print('Invalid rule in the rules source (not shown).')
            sys.exit(2)
if not pats:
    print('No rules available: refusing to pass without checking.')
    sys.exit(2)

files = subprocess.check_output(['git', '-C', str(root), 'ls-files', '-z'], text=True).split('\0')
hits = 0
checked = 0
for f in files:
    p = root / f
    if not f or not p.is_file():
        continue
    checked += 1
    if any(pat.search(f) for pat in pats):
        hits += 1
        print(f'{f}:name')
    raw = p.read_bytes()
    if raw[:2] == b'\x1f\x8b':  # gzip (structure .nbt)
        try:
            raw = gzip.decompress(raw)
        except Exception:
            pass
    if b'\0' in raw[:4096] and f.endswith('.png'):
        continue
    text = raw.decode('utf-8', errors='ignore') if b'\0' not in raw else raw.decode('latin-1')
    if b'\0' in raw:  # binary: look at printable runs
        text = '\n'.join(re.findall(r'[\x20-\x7e]{4,}', text))
    for n, line in enumerate(text.splitlines(), 1):
        if 'content-check: ignore' in line:
            continue
        if any(pat.search(line) for pat in pats):
            hits += 1
            print(f'{f}:{n}')
print(f'{hits} match(es) in {checked} files')
sys.exit(1 if hits else 0)
