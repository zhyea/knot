#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""修复「字符串字面量被误伤」：把 SQL 单引号内的新表名还原成旧表名。

背景：全词替换 \\b<table>\\b 会命中 'model.models'、'/api/users'、
'https://openrouter.ai/models' 这类字面量（. / - 都是词边界），
导致菜单编码、API 路径绑定、外部 URL 被改坏。

用法：python fix-string-literals.py <mapping.csv> <target.sql> [...]
"""
import re
import sys

mapfile, files = sys.argv[1], sys.argv[2:]
if not mapfile or not files:
    print("usage: fix-string-literals.py mapping.csv file.sql [...]")
    sys.exit(1)

rev = {}
with open(mapfile, encoding="utf-8-sig") as fh:
    for line in fh:
        line = line.strip().replace("\r", "")
        if not line or line.startswith("#"):
            continue
        parts = line.split(",")
        if len(parts) < 2:
            continue
        old, new = parts[0].strip(), parts[1].strip()
        if old and new:
            rev[new] = old

keys = sorted(rev, key=len, reverse=True)  # 长名优先
pat = re.compile(r"('[^']*')")
subs = [(re.compile(r"\b" + re.escape(k) + r"\b"), rev[k]) for k in keys]


def fix(match):
    text = match.group(1)
    for regex, old in subs:
        text = regex.sub(old, text)
    return text


for path in files:
    with open(path, encoding="utf-8", newline="") as fh:
        content = fh.read()
    new = pat.sub(fix, content)
    if new != content:
        with open(path, "w", encoding="utf-8", newline="") as fh:
            fh.write(new)
        print("fixed:", path)
    else:
        print("unchanged:", path)
