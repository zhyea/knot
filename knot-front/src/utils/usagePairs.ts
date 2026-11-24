import {EMPTY_PLACEHOLDER} from "@/utils/format";

/** 用量对照行：同一计费维度在「上游原文」与「knot_extend.usage」两侧的取值 */
export interface UsagePair {
  key: string;
  upstream: string;
  produced: string;
}

/** produced 侧的分段前缀，按「输出侧优先」排列：同名键先取 output，再取 input，最后 attributes */
const PRODUCED_SECTIONS = ["output", "input", "attributes"];

/**
 * 上游 usage 与 `knot_extend.usage` 按字段对齐。
 *
 * 上游多为扁平键（`image_count`），后者按「输入侧 / 输出侧 / 计费属性」三段组织（`output.image_count`），
 * 直接取键并集会把整段对象塞进一格、与上游字段错开。这里先展平成点路径，再按
 * 「同名 → output.同名 → input.同名 → attributes.同名 → 唯一后缀」查找对应值；
 * 两侧都对不上的 produced 键单独成行，不并进别人的格子。
 */
export function buildUsagePairs(
  upstream: Record<string, unknown> | null | undefined,
  produced: Record<string, unknown> | null | undefined
): UsagePair[] {
  const left = flatten(upstream ?? {});
  const right = flatten(produced ?? {});
  const consumed = new Set<string>();
  const rows: UsagePair[] = [];

  for (const key of Object.keys(left).sort()) {
    const matched = matchProduced(key, right, consumed);
    if (matched) {
      consumed.add(matched);
    }
    rows.push({
      key,
      upstream: formatCell(left[key]),
      produced: formatCell(matched ? right[matched] : undefined),
    });
  }

  for (const key of Object.keys(right).sort()) {
    if (consumed.has(key)) {
      continue;
    }
    rows.push({key, upstream: EMPTY_PLACEHOLDER, produced: formatCell(right[key])});
  }

  return rows;
}

/** 展平嵌套对象为点路径；数组与非普通对象按叶子保留（原样序列化展示） */
function flatten(source: Record<string, unknown>, prefix = ""): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(source)) {
    const path = prefix ? `${prefix}.${key}` : key;
    if (isPlainObject(value)) {
      Object.assign(out, flatten(value, path));
    } else {
      out[path] = value;
    }
  }
  return out;
}

/**
 * 为上游键找 produced 侧对应值。
 * 段内同名优先；唯一后缀兜底（`width` 对 `attributes.width`），有多个同后缀候选时不猜。
 */
function matchProduced(
  key: string,
  produced: Record<string, unknown>,
  consumed: Set<string>
): string | undefined {
  for (const section of PRODUCED_SECTIONS) {
    const candidate = `${section}.${key}`;
    if (candidate in produced && !consumed.has(candidate)) {
      return candidate;
    }
  }
  const suffixed = Object.keys(produced).filter(
    (path) => path.endsWith(`.${key}`) && !consumed.has(path)
  );
  return suffixed.length === 1 ? suffixed[0] : undefined;
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function formatCell(value: unknown): string {
  if (value == null || value === "") {
    return EMPTY_PLACEHOLDER;
  }
  if (typeof value === "object") {
    return JSON.stringify(value);
  }
  return String(value);
}
