package org.chobit.knot.admin.controller;

import jakarta.validation.Valid;
import org.chobit.knot.common.annotation.OperationLog;
import org.chobit.knot.svc.converter.RoutingTestPresetConverter;
import org.chobit.knot.svc.dto.routing.RoutingTestPresetDto;
import org.chobit.knot.common.model.PageQuery;
import org.chobit.knot.common.model.PageResult;
import org.chobit.knot.svc.service.RoutingTestPresetService;
import org.chobit.knot.svc.vo.common.EnabledStatusRequest;
import org.chobit.knot.svc.vo.routing.RoutingTestPreset;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/test-request-presets")
public class RoutingTestPresetController {
    private final RoutingTestPresetService presetService;
    private final RoutingTestPresetConverter presetConverter;

    public RoutingTestPresetController(RoutingTestPresetService presetService, RoutingTestPresetConverter presetConverter) {
        this.presetService = presetService;
        this.presetConverter = presetConverter;
    }

    @PostMapping("/list")
    public PageResult<RoutingTestPreset> list(@RequestBody(required = false) PageQuery query) {
        PageResult<RoutingTestPresetDto> page = presetService.list(
                PageQuery.toPageRequest(query),
                query == null ? null : query.keyword(),
                query == null ? null : query.protocol(),
                query == null ? null : query.modelFamilyCode()
        );
        return page.mapList(presetConverter::toVOList);
    }

    /** 预设详情：下拉选中后按 id 取完整请求体模板（调试上下文只带 id / 名称 / 协议 / 模型族）。 */
    @GetMapping("/{id}")
    public RoutingTestPreset get(@PathVariable Long id) {
        return presetConverter.toVO(presetService.get(id));
    }

    @OperationLog(module = "routing", operation = "CREATE", entityType = "TestRequestPreset",
            entityIdAfter = "#result.id()",
            entityNameAfter = "#result.name()")
    @PostMapping
    public RoutingTestPreset create(@RequestBody @Valid RoutingTestPreset request) {
        RoutingTestPresetDto created = presetService.create(presetConverter.toDto(request));
        return presetConverter.toVO(created);
    }

    @OperationLog(module = "routing", operation = "UPDATE", entityType = "TestRequestPreset",
            entityId = "#id",
            entityNameAfter = "#result.name()",
            oldValueSpel = "@routingTestPresetService.routingTestPresetAuditSnapshot(#p0)",
            newValueSpel = "@routingTestPresetService.routingTestPresetAuditSnapshot(#p0)")
    @PutMapping("/{id}")
    public RoutingTestPreset update(@PathVariable Long id, @RequestBody @Valid RoutingTestPreset request) {
        RoutingTestPresetDto updated = presetService.update(id, presetConverter.toDto(request));
        return presetConverter.toVO(updated);
    }

    @OperationLog(module = "routing", operation = "UPDATE", entityType = "TestRequestPreset",
            entityId = "#id",
            entityNameAfter = "#result.name()",
            oldValueSpel = "@routingTestPresetService.routingTestPresetAuditSnapshot(#p0)",
            newValueSpel = "@routingTestPresetService.routingTestPresetAuditSnapshot(#p0)")
    @PutMapping("/{id}/status")
    public RoutingTestPreset updateStatus(@PathVariable Long id, @RequestBody @Valid EnabledStatusRequest request) {
        RoutingTestPresetDto updated = presetService.updateStatus(id, Boolean.TRUE.equals(request.enabled()));
        return presetConverter.toVO(updated);
    }

    @OperationLog(module = "routing", operation = "DELETE", entityType = "TestRequestPreset",
            entityId = "#id")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        presetService.delete(id);
    }
}
