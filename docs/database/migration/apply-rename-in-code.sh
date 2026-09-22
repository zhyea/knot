#!/usr/bin/env bash
# 代码侧表名批量替换（仅在数据库 RENAME 迁移完成后、且与新包同窗口发布时使用）
# 用法（仓库根目录执行）：
#   DRY=1 bash docs/database/migration/apply-rename-in-code.sh   # 只预览不写盘
#   bash docs/database/migration/apply-rename-in-code.sh         # 实际替换
# 说明：
#   - 只处理 MyBatis mapper XML + schema.sql + data.sql（Java 里无 SQL，无需替换）
#   - 使用全词匹配 \b，不会误伤 provider_accounts / model_pools 等包含关系
#   - 只替换表名；索引名（uk_*/idx_*）不在本次范围，见方案文档第八节
#   - 幂等：可重复执行，可用于补齐被中断的替换
set -euo pipefail

cd "$(dirname "$0")/../../.."
ROOT="$(pwd)"
MAP="$ROOT/docs/database/migration/table-rename-mapping.csv"
RULES="$(mktemp)"
trap 'rm -f "$RULES"' EXIT

# 把映射编译成单份 sed 规则：对"每个文件执行一次 sed"，
# 而不是"每张表对每个文件执行一次"——后者 52×35=1820 次进程会被 120s 超时 SIGTERM，
# 实测导致替换只完成一半（31/35 文件）。
awk -F, '$1 !~ /#/ && NF>=2 {gsub(/\r/,""); printf "s/\\b%s\\b/%s/g\n", $1, $2}' "$MAP" > "$RULES"

mapfile -t FILES < <(find knot-server -name '*Mapper.xml' -not -path '*/target/*' | sort)
FILES+=(
  "$ROOT/knot-server/knot-admin/src/main/resources/db/schema.sql"
  "$ROOT/knot-server/knot-admin/src/main/resources/db/data.sql"
)

echo "规则条数: $(wc -l < "$RULES")，目标文件数: ${#FILES[@]}"

if [[ "${DRY:-0}" == "1" ]]; then
  echo "== DRY RUN，不做任何写入 =="
  while IFS=, read -r old new _rest; do
    [[ "$old" == \#* || -z "${new:-}" ]] && continue
    old="${old#$'\xef\xbb\xbf'}"
    total=$(grep -ohE "\b${old}\b" "${FILES[@]}" 2>/dev/null | wc -l)
    printf '%-32s -> %-32s (%s 处)\n' "$old" "$new" "$total"
  done < "$MAP"
  exit 0
fi

printf '%s\n' "${FILES[@]}" | tr '\n' '\0' | xargs -0 sed -i -f "$RULES"
echo "== 替换完成 =="

# 全词替换会误伤字符串字面量（. / - 都是词边界）：
#   'model.models' -> 'model.kb_models'、'/api/users' -> '/api/ks_users'、
#   'https://openrouter.ai/models' -> '.../kb_models'（会把外部同步 URL 改坏）
# 因此对 data.sql 立即执行一次字面量还原。
PY="${PYTHON:-}"
if [[ -z "$PY" ]]; then
  for cand in python3 python "C:/Users/robin/.workbuddy/binaries/python/versions/3.13.12/python.exe"; do
    if command -v "$cand" >/dev/null 2>&1 || [[ -x "$cand" ]]; then PY="$cand"; break; fi
  done
fi
if [[ -n "$PY" ]]; then
  # 用相对路径：本机 Git Bash 的 $ROOT 是 /d/... 形式，传给 Windows python 会被拼成 D:\d\...
  "$PY" docs/database/migration/fix-string-literals.py \
        docs/database/migration/table-rename-mapping.csv \
        knot-server/knot-admin/src/main/resources/db/data.sql
else
  echo "⚠️ 未找到 python，跳过字面量还原；请手动执行 fix-string-literals.py"
fi

OLD=$(awk -F, '$1 !~ /#/ && NF>=2 {gsub(/\r/,""); print $1}' "$MAP" | paste -sd'|')
echo "残留检查（应仅剩字符串字面量与 REST 路径，需人工确认）："
printf '%s\n' "${FILES[@]}" | tr '\n' '\0' | xargs -0 grep -nE "\b(${OLD})\b" || echo "(无残留)"
echo "字面量误伤检查（应为空）："
grep -nE "'[^']*k[sbxr]_[a-z_]+" "$ROOT/knot-server/knot-admin/src/main/resources/db/data.sql" || echo "(无字面量误伤)"
