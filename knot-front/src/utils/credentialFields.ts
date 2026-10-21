import type {Dict} from "@/types";

/**
 * 认证字段定义：由后端 {@code ProviderCredentialTypeEnum.requiredFields} 下发。
 *
 * <p>键名沿用 {@code requiredFields}（历史契约），内容已升级为**字段定义数组**：
 * {@code {name,label,type,required,options,default,placeholder}}。前端不再维护
 * 「哪些字段是密码 / 哪个字段用文本域 / 哪些值可选」这类映射表，一律按节点渲染。</p>
 */
export interface CredentialFieldOption {
  /** 提交到 authConfig 的值 */
  value: string;
  /** 展示文案，缺省回落 value */
  label?: string;
}

export interface CredentialFieldDef {
  /** 提交到 authConfig 的键名 */
  name: string;
  label?: string;
  /** 控件类型：text / password / textarea / select；未知类型按 text 兜底 */
  type?: string;
  /** 未声明时按必填处理（与后端 requiredFieldNames 同口径） */
  required?: boolean;
  placeholder?: string;
  default?: string;
  options?: CredentialFieldOption[];
}

export type CredentialInputType = "text" | "password" | "textarea" | "select";

/**
 * 归一化后端下发的字段定义数组：剔除非对象/无 name 节点、按 name 去重、规整 options。
 *
 * <p>后端未升级或字段结构被改坏时，这里宁可少渲染一个字段，也不产出半截控件。</p>
 */
export function normalizeCredentialFields(raw: unknown): CredentialFieldDef[] {
  if (!Array.isArray(raw)) {
    return [];
  }
  const seen = new Set<string>();
  const fields: CredentialFieldDef[] = [];
  for (const item of raw) {
    if (!item || typeof item !== "object") continue;
    const field = item as CredentialFieldDef;
    const name = typeof field.name === "string" ? field.name.trim() : "";
    if (!name || seen.has(name)) continue;
    seen.add(name);
    fields.push({...field, name, options: normalizeOptions(field.options)});
  }
  return fields;
}

function normalizeOptions(raw: unknown): CredentialFieldOption[] {
  if (!Array.isArray(raw)) {
    return [];
  }
  return raw
    .filter((item) => item && typeof item === "object")
    .map((item) => item as CredentialFieldOption)
    .filter((option) => String(option.value ?? "").trim() !== "")
    .map((option) => ({
      value: String(option.value),
      label: String(option.label ?? "").trim() || String(option.value)
    }));
}

/** 表单标签：优先后端 label，缺失回落字段名 */
export function credentialFieldLabel(field: CredentialFieldDef): string {
  return field.label?.trim() || field.name;
}

/** 未显式声明 required 一律按必填（后端同样按必填处理） */
export function isCredentialFieldRequired(field: CredentialFieldDef): boolean {
  return field.required !== false;
}

/** 控件类型：下拉由 type=select 驱动，未知类型兜底文本框 */
export function credentialInputType(field: CredentialFieldDef): CredentialInputType {
  const type = (field.type || "").trim().toLowerCase();
  if (type === "select" || type === "password" || type === "textarea") {
    return type;
  }
  return "text";
}

/**
 * 按字段定义重建 authConfig：优先保留当前已填值，其次取历史键值，最后回落字段 default。
 *
 * <p>只产出定义内的键；定义外的键（自定义 KV 区）由调用方单独合并。
 * 空串视为未填，避免把空白值当作用户输入钉死在表单里。</p>
 */
export function buildCredentialValues(defs: CredentialFieldDef[],
                                      current?: Dict | null,
                                      fallback?: Dict | null): Dict {
  const values: Dict = {};
  for (const field of defs) {
    const kept = filledValue(current?.[field.name]) || filledValue(fallback?.[field.name]);
    values[field.name] = kept || field.default || "";
  }
  return values;
}

function filledValue(value: unknown): string {
  if (value == null) {
    return "";
  }
  const text = String(value);
  return text.trim() === "" ? "" : text;
}
