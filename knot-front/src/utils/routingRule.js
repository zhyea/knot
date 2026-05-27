/** 生成路由规则编码（32 位十六进制，与后端 RoutingRuleCodeGenerator 一致） */
export function generateRoutingRuleCode() {
  return crypto.randomUUID().replaceAll("-", "");
}

/** 构建路由规则更新载荷（含启用开关切换） */
export function buildRoutingRulePayload(row, enabled) {
  return {
    ruleCode: row.ruleCode,
    name: row.name,
    appScenario: row.appScenario ?? null,
    modelTypes: Array.isArray(row.modelTypes) && row.modelTypes.length ? row.modelTypes : ["CHAT"],
    consumerIds: Array.isArray(row.consumerIds) ? row.consumerIds : [],
    appId: row.appId,
    userId: row.userId ?? null,
    strategy: row.strategy,
    enabled,
    models: (row.models || []).map((m) => ({
      modelId: m.modelId,
      priority: m.priority ?? 100,
      primary: !!m.primary
    })),
    rateLimitPolicy: row.rateLimitPolicy ?? null,
    quotaPolicy: row.quotaPolicy ?? null
  };
}
