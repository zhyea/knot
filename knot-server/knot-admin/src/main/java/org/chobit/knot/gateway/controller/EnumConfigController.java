package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.annotation.OperationLog;
import org.chobit.knot.gateway.entity.EnumCategorySummary;
import org.chobit.knot.gateway.entity.EnumConfigEntity;
import org.chobit.knot.gateway.entity.OperationLogEntity;
import org.chobit.knot.gateway.model.PageQuery;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.service.EnumConfigService;
import org.chobit.knot.gateway.service.OperationLogService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/enums")
public class EnumConfigController {

    private final EnumConfigService enumConfigService;
    private final OperationLogService operationLogService;

    public EnumConfigController(EnumConfigService enumConfigService, OperationLogService operationLogService) {
        this.enumConfigService = enumConfigService;
        this.operationLogService = operationLogService;
    }

    /** 分页查询所有枚举项（管理后台全量列表用） */
    @PostMapping("/list")
    public PageResult<EnumConfigEntity> list(@RequestBody(required = false) PageQuery query) {
        PageRequest pr = query == null ? PageRequest.of(1, 20) : query.toPageRequest();
        return enumConfigService.list(pr, query != null ? query.category() : null);
    }

    /** 枚举分类聚合列表（分类管理首页） */
    @PostMapping("/category-summaries")
    public List<EnumCategorySummary> categorySummaries() {
        return enumConfigService.listCategorySummaries();
    }

    /** 按分类查询枚举项 */
    @GetMapping("/items/{category}")
    public List<EnumConfigEntity> listItems(@PathVariable String category) {
        return enumConfigService.listByCategory(category);
    }

    /** 某分类下枚举配置相关的操作日志 */
    @GetMapping("/operation-logs/{category}")
    public List<OperationLogEntity> operationLogsByCategory(@PathVariable String category) {
        return operationLogService.listForEnumCategory(category);
    }

    /** 查询所有分类编码 */
    @PostMapping("/categories")
    public List<String> listCategories() {
        return enumConfigService.listCategories();
    }

    /** 新增枚举项 */
    @OperationLog(module = "enum", operation = "CREATE", entityType = "EnumConfig",
            entityIdAfter = "#result.id",
            entityNameAfter = "#result.category + '/' + #result.itemCode",
            description = "'新增枚举值'",
            recordNewValue = true,
            newValueSpel = "#result")
    @PostMapping
    public EnumConfigEntity create(@RequestBody EnumConfigEntity request) {
        return enumConfigService.create(request);
    }

    /** 修改枚举项 */
    @OperationLog(module = "enum", operation = "UPDATE", entityType = "EnumConfig",
            entityId = "#p0",
            entityNameAfter = "#result.category + '/' + #result.itemCode",
            description = "'更新枚举值'",
            recordOldValue = true,
            oldValueSpel = "@enumConfigService.getById(#p0)",
            recordNewValue = true,
            newValueSpel = "#result")
    @PutMapping("/{id}")
    public EnumConfigEntity update(@PathVariable Long id, @RequestBody EnumConfigEntity request) {
        return enumConfigService.update(id, request);
    }

    /** 删除枚举项 */
    @OperationLog(module = "enum", operation = "DELETE", entityType = "EnumConfig",
            entityId = "#p0",
            entityNameAfter = "#result.category + '/' + #result.itemCode",
            description = "'删除枚举值'",
            recordOldValue = true,
            oldValueSpel = "@enumConfigService.getById(#p0)")
    @DeleteMapping("/{id}")
    public EnumConfigEntity delete(@PathVariable Long id) {
        return enumConfigService.deleteReturning(id);
    }
}
