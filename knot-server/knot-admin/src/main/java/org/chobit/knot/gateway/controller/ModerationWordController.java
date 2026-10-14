package org.chobit.knot.gateway.controller;

import jakarta.validation.Valid;
import org.chobit.knot.gateway.annotation.OperationLog;
import org.chobit.knot.gateway.converter.ModerationWordConverter;
import org.chobit.knot.gateway.dto.moderation.ModerationWordDto;
import org.chobit.knot.gateway.dto.moderation.ModerationWordImportItem;
import org.chobit.knot.gateway.dto.moderation.ModerationWordImportResult;
import org.chobit.knot.gateway.model.PageQuery;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.service.ModerationWordService;
import org.chobit.knot.gateway.vo.common.CodeAvailability;
import org.chobit.knot.gateway.vo.common.EnabledStatusRequest;
import org.chobit.knot.gateway.vo.moderation.ModerationWordItem;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/moderation-words")
public class ModerationWordController {

    private final ModerationWordService moderationWordService;
    private final ModerationWordConverter moderationWordConverter;

    /**
     * Constructs a new instance.
     */
    public ModerationWordController(ModerationWordService moderationWordService,
                                    ModerationWordConverter moderationWordConverter) {
        this.moderationWordService = moderationWordService;
        this.moderationWordConverter = moderationWordConverter;
    }

    /**
     * Checks whether the requested condition is satisfied. Executes the public operation.
     *
     * <p>唯一键是 (word, category_code)，故可用性判断要同时带分类。</p>
     */
    @GetMapping("/check-code")
    public CodeAvailability checkCode(@RequestParam String word,
                                      @RequestParam String categoryCode,
                                      @RequestParam(required = false) Long excludeId) {
        return new CodeAvailability(moderationWordService.isWordAvailable(word, categoryCode, excludeId));
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    @GetMapping("/{id}")
    public ModerationWordItem get(@PathVariable Long id) {
        return moderationWordConverter.toVO(moderationWordService.getById(id));
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    @PostMapping("/list")
    public PageResult<ModerationWordItem> list(@RequestBody(required = false) PageQuery query,
                                               @RequestParam(required = false) String categoryCode) {
        PageQuery effective = query == null
                ? new PageQuery(null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null)
                : query;
        PageResult<ModerationWordDto> page = moderationWordService.list(
                effective.toPageRequest(),
                effective.keyword(),
                categoryCode,
                effective.includeDeleted());
        return new PageResult<>(
                page.list().stream().map(moderationWordConverter::toVO).toList(),
                page.total(), page.pageNum(), page.pageSize(), page.pages());
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    @OperationLog(module = "moderation", operation = "CREATE", entityType = "ModerationWord",
            entityIdAfter = "#result.id()",
            entityNameAfter = "#result.word()",
            description = "'新建敏感词'",
            newValueSpel = "@moderationWordService.auditSnapshot(#result.id())")
    @PostMapping
    public ModerationWordItem create(@RequestBody @Valid ModerationWordItem request) {
        return moderationWordConverter.toVO(
                moderationWordService.create(toDto(request)));
    }

    /**
     * Updates the target resource. Executes the public operation.
     */
    @OperationLog(module = "moderation", operation = "UPDATE", entityType = "ModerationWord",
            entityId = "#p0",
            entityNameAfter = "#result.word()",
            description = "'更新敏感词'",
            oldValueSpel = "@moderationWordService.auditSnapshot(#p0)",
            newValueSpel = "@moderationWordService.auditSnapshot(#p0)")
    @PutMapping("/{id}")
    public ModerationWordItem update(@PathVariable Long id, @RequestBody @Valid ModerationWordItem request) {
        return moderationWordConverter.toVO(
                moderationWordService.update(id, toDto(request)));
    }

    /**
     * Updates the sensitive word enabled status.
     */
    @OperationLog(module = "moderation", operation = "UPDATE", entityType = "ModerationWord",
            entityId = "#p0",
            entityNameAfter = "#result.word()",
            description = "'更新敏感词状态'",
            oldValueSpel = "@moderationWordService.auditSnapshot(#p0)",
            newValueSpel = "@moderationWordService.auditSnapshot(#p0)")
    @PutMapping("/{id}/status")
    public ModerationWordItem updateStatus(@PathVariable Long id,
                                           @RequestBody @Valid EnabledStatusRequest request) {
        return moderationWordConverter.toVO(
                moderationWordService.updateStatus(id, Boolean.TRUE.equals(request.enabled())));
    }

    /**
     * Deletes the target resource. Executes the public operation.
     */
    @OperationLog(module = "moderation", operation = "DELETE", entityType = "ModerationWord",
            entityId = "#p0",
            description = "'删除敏感词'",
            oldValueSpel = "@moderationWordService.auditSnapshot(#p0)")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        moderationWordService.delete(id);
    }

    /**
     * 恢复已逻辑删除的敏感词。唯一键按物理行判定，删除后同词无法新建，只能通过本接口恢复。
     */
    @OperationLog(module = "moderation", operation = "UPDATE", entityType = "ModerationWord",
            entityId = "#p0",
            entityNameAfter = "#result.word()",
            description = "'恢复敏感词'",
            oldValueSpel = "@moderationWordService.auditSnapshot(#p0)",
            newValueSpel = "@moderationWordService.auditSnapshot(#p0)")
    @PutMapping("/{id}/restore")
    public ModerationWordItem restore(@PathVariable Long id) {
        return moderationWordConverter.toVO(moderationWordService.restore(id));
    }

    /**
     * 批量导入：逐行独立校验，部分失败不影响已成功的行。
     */
    @OperationLog(module = "moderation", operation = "IMPORT", entityType = "ModerationWord",
            description = "'批量导入敏感词'",
            newValueSpel = "#result")
    @PostMapping("/import")
    public ModerationWordImportResult importWords(@RequestBody List<ModerationWordImportItem> rows) {
        return moderationWordService.importWords(rows);
    }

    /**
     * 导出全部未删除词条（含停用）。
     */
    @GetMapping("/export")
    public List<ModerationWordItem> export() {
        return moderationWordService.exportWords().stream()
                .map(moderationWordConverter::toVO)
                .toList();
    }

    private static ModerationWordDto toDto(ModerationWordItem item) {
        return new ModerationWordDto(item.id(), item.word(), item.categoryCode(), item.severity(),
                item.lang(), item.enabled(), item.deleted(), item.remark(), null, null, null);
    }
}
