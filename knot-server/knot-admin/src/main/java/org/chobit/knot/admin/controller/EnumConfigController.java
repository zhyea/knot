package org.chobit.knot.admin.controller;

import org.chobit.knot.common.annotation.OperationLog;
import org.chobit.knot.svc.dto.system.EnumCategoryQuery;
import org.chobit.knot.common.model.PageQuery;
import org.chobit.knot.common.model.PageRequest;
import org.chobit.knot.common.model.PageResult;
import org.chobit.knot.svc.service.EnumConfigService;
import org.chobit.knot.svc.service.OperationLogService;
import org.chobit.knot.svc.vo.system.EnumCategorySummaryItem;
import org.chobit.knot.svc.vo.system.EnumConfigItem;
import org.chobit.knot.svc.vo.system.OperationLogDetailItem;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/enums")
public class EnumConfigController {

    private final EnumConfigService enumConfigService;
    private final OperationLogService operationLogService;

    /**
     * Constructs a new instance.
     */
    public EnumConfigController(EnumConfigService enumConfigService, OperationLogService operationLogService) {
        this.enumConfigService = enumConfigService;
        this.operationLogService = operationLogService;
    }

    /**
     * 分页查询所有枚举项。
     */
    @PostMapping("/list")
    public PageResult<EnumConfigItem> list(@RequestBody(required = false) PageQuery query) {
        PageRequest pr = query == null ? PageRequest.of(1, 20) : query.toPageRequest();
        return enumConfigService.list(pr, query != null ? query.category() : null);
    }

    /**
     * 查询枚举分类聚合列表。
     */
    @PostMapping("/category-summaries")
    public List<EnumCategorySummaryItem> categorySummaries(@RequestBody(required = false) EnumCategoryQuery query) {
        return enumConfigService.listCategorySummaries(query != null ? query.keyword() : null);
    }

    /**
     * 按分类查询枚举项。
     */
    @GetMapping("/items/{category}")
    public List<EnumConfigItem> listItems(@PathVariable String category) {
        return enumConfigService.listByCategory(category);
    }

    /**
     * 查询某个分类下相关的操作日志。
     */
    @GetMapping("/operation-logs/{category}")
    public List<OperationLogDetailItem> operationLogsByCategory(@PathVariable String category) {
        return operationLogService.listForEnumCategory(category);
    }

    /**
     * 查询全部分类编码。
     */
    @PostMapping("/categories")
    public List<String> listCategories() {
        return enumConfigService.listCategories();
    }

    /**
     * 创建枚举项。
     */
    @OperationLog(module = "enum", operation = "CREATE", entityType = "EnumConfig",
            entityIdAfter = "#result.id()",
            entityNameAfter = "#result.category() + '/' + #result.itemCode()",
            description = "'创建枚举值'",
            newValueSpel = "#result")
    @PostMapping
    public EnumConfigItem create(@RequestBody EnumConfigItem request) {
        return enumConfigService.create(request);
    }

    /**
     * 更新枚举项。
     */
    @OperationLog(module = "enum", operation = "UPDATE", entityType = "EnumConfig",
            entityId = "#p0",
            entityNameAfter = "#result.category() + '/' + #result.itemCode()",
            description = "'更新枚举值'",
            oldValueSpel = "@enumConfigService.getById(#p0)",
            newValueSpel = "#result")
    @PutMapping("/{id}")
    public EnumConfigItem update(@PathVariable Long id, @RequestBody EnumConfigItem request) {
        return enumConfigService.update(id, request);
    }

    /**
     * 删除枚举项。
     */
    @OperationLog(module = "enum", operation = "DELETE", entityType = "EnumConfig",
            entityId = "#p0",
            entityNameAfter = "#result.category() + '/' + #result.itemCode()",
            description = "'删除枚举值'",
            oldValueSpel = "@enumConfigService.getById(#p0)")
    @DeleteMapping("/{id}")
    public EnumConfigItem delete(@PathVariable Long id) {
        return enumConfigService.deleteReturning(id);
    }
}
