#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""把 docs/standards/rules/*.md 分发成各 AI 工具的规则文件。

用法（仓库根）：
    python docs/standards/generate-tool-rules.py

生成物（勿手工编辑，下次生成会覆盖）：
    .cursor/rules/*.mdc    Cursor（description / globs / alwaysApply）
    .trae/rules/*.md       Trae（同 Cursor 款 frontmatter）
    .qoder/rules/*.md      Qoder（trigger: always_on | glob | model_decision）

权威源：docs/standards/rules/*.md —— 改规范只改那里。
"""

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RULES_DIR = os.path.join(ROOT, "docs", "standards", "rules")

# 域 -> (mode, description, globs)
#   mode: "always" | "glob" | "smart"
SPECS = {
    "00-core": (
        "always",
        "knot 项目总则、门禁命令与铁律，所有会话必读",
        [],
    ),
    "10-backend-java": (
        "glob",
        "knot 后端 Java 规范：模块依赖红线、分层、命名纪律、编码细节、异常与测试",
        ["knot-server/**/*.java"],
    ),
    "20-frontend-vue": (
        "glob",
        "knot 前端 Vue3 + TypeScript 规范：类型约定、已知陷阱、既定模式",
        ["knot-front/src/**"],
    ),
    "30-database-sql": (
        "glob",
        "knot 数据库规范：表前缀、迁移脚本、逻辑删除四必配、冒烟库",
        ["**/*.sql", "**/db/**"],
    ),
    "40-plugin": (
        "smart",
        "knot 插件架构与敏感词审核拓扑：涉及 plugin、敏感词、插件实例表时使用",
        ["knot-server/knot-plugin*/**"],
    ),
    "50-verification": (
        "smart",
        "knot 验证规范：三道门禁、真启 jar 冒烟、测试与并发收口纪律，改动收口前使用",
        [],
    ),
    "60-business-rules": (
        "smart",
        "knot 业务域规则：计费、频控额度、重试、模型路由、流式与 knot_extend",
        [],
    ),
    "70-delivery": (
        "smart",
        "knot 交付规范：产物落盘位置、git 纪律、变更收口检查表、沟通约定",
        [],
    ),
}

HEADER_NOTE = "> 本文件由 `docs/standards/generate-tool-rules.py` 自动生成，**勿手工编辑**。权威源：`docs/standards/rules/{src}`。\n"


def read_rule(name):
    path = os.path.join(RULES_DIR, name + ".md")
    if not os.path.isfile(path):
        raise SystemExit("缺少规则源文件: %s" % path)
    with open(path, "r", encoding="utf-8") as fh:
        return fh.read().strip()


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)
    print("  -> %s" % os.path.relpath(path, ROOT))


def yaml_list(items):
    if not items:
        return "[]"
    return "[" + ", ".join('"%s"' % i for i in items) + "]"


def build_cursor(name, body, mode, desc, globs):
    if mode == "always":
        fm = "---\ndescription: %s\nalwaysApply: true\n---\n" % desc
    elif mode == "glob":
        fm = "---\ndescription: %s\nglobs: %s\nalwaysApply: false\n---\n" % (desc, yaml_list(globs))
    else:
        fm = "---\ndescription: %s\nalwaysApply: false\n---\n" % desc
    return fm + "\n" + body + "\n"


def build_qoder(name, body, mode, desc, globs):
    if mode == "always":
        fm = "---\ntrigger: always_on\ndescription: %s\n---\n" % desc
    elif mode == "glob":
        fm = "---\ntrigger: glob\ndescription: %s\nglob: %s\n---\n" % (desc, ", ".join(globs))
    else:
        fm = "---\ntrigger: model_decision\ndescription: %s\n---\n" % desc
    return fm + "\n" + body + "\n"


def main():
    names = sorted(SPECS)
    print("生成工具规则（源：docs/standards/rules/）")
    for name in names:
        mode, desc, globs = SPECS[name]
        body = read_rule(name)
        body = HEADER_NOTE.format(src="rules/%s.md" % name) + "\n" + body

        write(os.path.join(ROOT, ".cursor", "rules", name + ".mdc"),
              build_cursor(name, body, mode, desc, globs))
        write(os.path.join(ROOT, ".trae", "rules", name + ".md"),
              build_cursor(name, body, mode, desc, globs))
        write(os.path.join(ROOT, ".qoder", "rules", name + ".md"),
              build_qoder(name, body, mode, desc, globs))
    print("完成：%d 个域 × 3 个工具 = %d 个文件" % (len(names), len(names) * 3))


if __name__ == "__main__":
    sys.exit(main())
