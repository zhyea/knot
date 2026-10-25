package org.chobit.knot.admin.controller;

import org.chobit.knot.common.annotation.OperationLog;
import org.chobit.knot.common.model.PageQuery;
import org.chobit.knot.common.model.PageRequest;
import org.chobit.knot.common.model.PageResult;
import org.chobit.knot.svc.converter.PluginConverter;
import org.chobit.knot.svc.dto.plugin.PluginDto;
import org.chobit.knot.svc.service.PluginService;
import org.chobit.knot.svc.vo.plugin.PluginItem;
import org.chobit.knot.svc.vo.plugin.PluginStatusRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/plugins")
public class PluginController {
    private final PluginService pluginService;
    private final PluginConverter pluginConverter;

    /**
     * Constructs a new instance.
     */
    public PluginController(PluginService pluginService, PluginConverter pluginConverter) {
        this.pluginService = pluginService;
        this.pluginConverter = pluginConverter;
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    @PostMapping("/list")
    public PageResult<PluginItem> list(@RequestBody(required = false) PageQuery query) {
        PageResult<PluginDto> page = pluginService.list(
                query == null ? PageRequest.of(1, 20) : query.toPageRequest(),
                query == null ? null : query.keyword(),
                query == null ? null : query.status(),
                query == null ? null : query.pluginType()
        );
        return page.mapList(pluginConverter::toVOList);
    }

    /**
     * 逻辑删除插件（死行不展示）。reconcile 标记 orphaned 的插件由前端删除按钮触发。
     */
    @OperationLog(module = "plugin", operation = "DELETE", entityType = "Plugin",
            entityId = "#p0",
            entityNameAfter = "#result.name",
            description = "'逻辑删除插件'",
            oldValueSpel = "@pluginService.getById(#p0)")
    @DeleteMapping("/{id}")
    public PluginItem delete(@PathVariable Long id) {
        PluginDto deleted = pluginService.markDeleted(id);
        return pluginConverter.toVO(deleted);
    }

    /**
     * 启用/禁用插件。状态语义为 EnabledStatusEnum（1-启用 0-停用）。
     */
    @OperationLog(module = "plugin", operation = "UPDATE", entityType = "Plugin",
            entityId = "#p0",
            entityNameAfter = "#result.name",
            description = "'更新插件状态'",
            oldValueSpel = "@pluginService.getById(#p0)",
            newValueSpel = "#result")
    @PutMapping("/{id}/status")
    public PluginItem updateStatus(@PathVariable Long id, @RequestBody @Valid PluginStatusRequest request) {
        PluginDto updated = pluginService.updateStatus(id, request.status());
        return pluginConverter.toVO(updated);
    }

}
