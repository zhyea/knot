import {describe, expect, it} from "vitest";
import {
  buildCredentialValues,
  credentialFieldLabel,
  credentialInputType,
  isCredentialFieldRequired,
  normalizeCredentialFields,
  type CredentialFieldDef
} from "@/utils/credentialFields";

/**
 * 契约来源：后端 ProviderCredentialTypeEnum.requiredFields（JsonNode 数组）。
 * 这里按后端真实下发的节点形态喂数据，钉死「type 决定控件 / options 决定下拉 / required 缺省必填」
 * 三条规则——前端不再有字段名硬编码映射表，规则一旦回退，这些用例立刻变红。
 */
const AWS_FIELDS: CredentialFieldDef[] = [
  {name: "accessKey", label: "Access Key ID", type: "text", required: true, placeholder: "AKIA..."},
  {name: "secretKey", label: "Secret Access Key", type: "password", required: true},
  {
    name: "region",
    label: "Region",
    type: "select",
    required: true,
    default: "us-east-1",
    options: [
      {value: "us-east-1", label: "us-east-1 (US East, N. Virginia)"},
      {value: "cn-north-1", label: "cn-north-1 (China, Beijing)"}
    ]
  }
];

describe("normalizeCredentialFields", () => {
  it("非数组输入返回空数组（后端未升级时不炸表单）", () => {
    expect(normalizeCredentialFields(undefined)).toEqual([]);
    expect(normalizeCredentialFields(null)).toEqual([]);
    expect(normalizeCredentialFields("apiKey")).toEqual([]);
    expect(normalizeCredentialFields([{name: "apiKey"}])).toHaveLength(1);
  });

  it("剔除无 name / 非对象节点，并按 name 去重（保留首次出现）", () => {
    const fields = normalizeCredentialFields([
      {name: "apiKey", label: "第一次"},
      {label: "没有 name"},
      null,
      "apiKey",
      {name: "  ", label: "空白 name"},
      {name: "apiKey", label: "重复"}
    ]);
    expect(fields.map((f) => f.name)).toEqual(["apiKey"]);
    expect(fields[0].label).toBe("第一次");
  });

  it("options 归一：去掉空 value，label 缺失回落 value", () => {
    const [region] = normalizeCredentialFields([
      {name: "region", type: "select", options: [{value: "us-east-1"}, {value: ""}, {label: "无值"}]}
    ]);
    expect(region.options).toEqual([{value: "us-east-1", label: "us-east-1"}]);
  });
});

describe("字段渲染规则", () => {
  it("label 缺失回落 name", () => {
    expect(credentialFieldLabel({name: "apiKey"})).toBe("apiKey");
    expect(credentialFieldLabel({name: "apiKey", label: "  API Key  "})).toBe("API Key");
  });

  it("required 未声明按必填，显式 false 才选填", () => {
    expect(isCredentialFieldRequired({name: "apiKey"})).toBe(true);
    expect(isCredentialFieldRequired({name: "apiKey", required: true})).toBe(true);
    expect(isCredentialFieldRequired({name: "sessionToken", required: false})).toBe(false);
  });

  it("type 决定控件：select/password/textarea 直通，未知与缺失兜底 text", () => {
    expect(credentialInputType({name: "region", type: "select"})).toBe("select");
    expect(credentialInputType({name: "secretKey", type: "password"})).toBe("password");
    expect(credentialInputType({name: "account_json", type: "textarea"})).toBe("textarea");
    expect(credentialInputType({name: "accessKey", type: "text"})).toBe("text");
    expect(credentialInputType({name: "x", type: "PASSWORD"})).toBe("password");
    expect(credentialInputType({name: "x", type: "unknown"})).toBe("text");
    expect(credentialInputType({name: "x"})).toBe("text");
  });
});

describe("buildCredentialValues（切换认证类型时的取值优先级）", () => {
  it("已填值优先于历史键值，历史键值优先于 default", () => {
    const values = buildCredentialValues(
      AWS_FIELDS,
      {accessKey: "AKIA-CURRENT"},
      {accessKey: "AKIA-OLD", secretKey: "SECRET-OLD"}
    );
    expect(values).toEqual({
      accessKey: "AKIA-CURRENT",
      secretKey: "SECRET-OLD",
      region: "us-east-1"
    });
  });

  it("空白值视为未填，回落 default", () => {
    const values = buildCredentialValues(AWS_FIELDS, {accessKey: "  ", region: ""});
    expect(values.accessKey).toBe("");
    expect(values.region).toBe("us-east-1");
  });

  it("只产出定义内的键（定义外残留由调用方当自定义 KV 处理）", () => {
    const values = buildCredentialValues(AWS_FIELDS, {legacyKey: "keep?"});
    expect(Object.keys(values)).toEqual(["accessKey", "secretKey", "region"]);
  });
});
