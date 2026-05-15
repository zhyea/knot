package org.chobit.knot.gateway.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/modules")
public class ModuleCatalogController {

    @PostMapping
    public List<ModuleInfo> listModules() {
        return List.of(
                new ModuleInfo("system", "系统管理", List.of("用户管理", "角色权限", "组织机构", "系统运维", "日志管理")),
                new ModuleInfo("providers", "供应商管理", List.of("供应商认证", "监控", "频控", "额度")),
                new ModuleInfo("models", "模型管理", List.of("模型配置", "版本管理", "测试调试", "频控额度")),
                new ModuleInfo("apps", "应用管理", List.of("应用认证", "授权", "配额", "调用监控")),
                new ModuleInfo("routing", "模型路由规则", List.of("固定路由", "权重路由", "故障转移", "成本优先")),
                new ModuleInfo("billing", "计费与成本", List.of("计费规则", "成本统计", "对账结算")),
                new ModuleInfo("security", "安全与监控", List.of("接口安全", "链路监控", "缓存管理", "告警管理"))
        );
    }

    public record ModuleInfo(String code, String name, List<String> capabilities) {
    }
}
