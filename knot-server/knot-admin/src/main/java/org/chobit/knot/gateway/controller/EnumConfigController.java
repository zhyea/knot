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

    /**
     * Constructs a new instance.
     */
    public EnumConfigController(EnumConfigService enumConfigService, OperationLogService operationLogService) {
        this.enumConfigService = enumConfigService;
        this.operationLogService = operationLogService;
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    /** 鍒嗛〉鏌ヨ鎵€鏈夋灇涓鹃」锛堢鐞嗗悗鍙板叏閲忓垪琛ㄧ敤锛?*/
    @PostMapping("/list")
    public PageResult<EnumConfigEntity> list(@RequestBody(required = false) PageQuery query) {
        PageRequest pr = query == null ? PageRequest.of(1, 20) : query.toPageRequest();
        return enumConfigService.list(pr, query != null ? query.category() : null);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    /** 鏋氫妇鍒嗙被鑱氬悎鍒楄〃锛堝垎绫荤鐞嗛椤碉級 */
    @PostMapping("/category-summaries")
    public List<EnumCategorySummary> categorySummaries() {
        return enumConfigService.listCategorySummaries();
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    /** 鎸夊垎绫绘煡璇㈡灇涓鹃」 */
    @GetMapping("/items/{category}")
    public List<EnumConfigEntity> listItems(@PathVariable String category) {
        return enumConfigService.listByCategory(category);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    /** 鏌愬垎绫讳笅鏋氫妇閰嶇疆鐩稿叧鐨勬搷浣滄棩蹇?*/
    @GetMapping("/operation-logs/{category}")
    public List<OperationLogEntity> operationLogsByCategory(@PathVariable String category) {
        return operationLogService.listForEnumCategory(category);
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    /** 鏌ヨ鎵€鏈夊垎绫荤紪鐮?*/
    @PostMapping("/categories")
    public List<String> listCategories() {
        return enumConfigService.listCategories();
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    /** 鏂板鏋氫妇椤?*/
    @OperationLog(module = "enum", operation = "CREATE", entityType = "EnumConfig",
            entityIdAfter = "#result.id",
            entityNameAfter = "#result.category + '/' + #result.itemCode",
            description = "'鏂板鏋氫妇鍊?",
            recordNewValue = true,
            newValueSpel = "#result")
    @PostMapping
    /**
     * Creates a new resource.
     */
    public EnumConfigEntity create(@RequestBody EnumConfigEntity request) {
        return enumConfigService.create(request);
    }

    /**
     * Updates the target resource. Executes the public operation.
     */
    /** 淇敼鏋氫妇椤?*/
    @OperationLog(module = "enum", operation = "UPDATE", entityType = "EnumConfig",
            entityId = "#p0",
            entityNameAfter = "#result.category + '/' + #result.itemCode",
            description = "'鏇存柊鏋氫妇鍊?",
            recordOldValue = true,
            oldValueSpel = "@enumConfigService.getById(#p0)",
            recordNewValue = true,
            newValueSpel = "#result")
    @PutMapping("/{id}")
    /**
     * Updates the target resource.
     */
    public EnumConfigEntity update(@PathVariable Long id, @RequestBody EnumConfigEntity request) {
        return enumConfigService.update(id, request);
    }

    /**
     * Deletes the target resource. Executes the public operation.
     */
    /** 鍒犻櫎鏋氫妇椤?*/
    @OperationLog(module = "enum", operation = "DELETE", entityType = "EnumConfig",
            entityId = "#p0",
            entityNameAfter = "#result.category + '/' + #result.itemCode",
            description = "'鍒犻櫎鏋氫妇鍊?",
            recordOldValue = true,
            oldValueSpel = "@enumConfigService.getById(#p0)")
    @DeleteMapping("/{id}")
    /**
     * Deletes the target resource.
     */
    public EnumConfigEntity delete(@PathVariable Long id) {
        return enumConfigService.deleteReturning(id);
    }
}
