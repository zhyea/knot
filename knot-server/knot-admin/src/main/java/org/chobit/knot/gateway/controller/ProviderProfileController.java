package org.chobit.knot.gateway.controller;

import jakarta.validation.Valid;
import org.chobit.knot.gateway.annotation.OperationLog;
import org.chobit.knot.gateway.model.PageQuery;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.model.ProviderProfileOptionQuery;
import org.chobit.knot.gateway.service.OptionsService;
import org.chobit.knot.gateway.service.ProviderProfileService;
import org.chobit.knot.gateway.vo.common.CodeAvailability;
import org.chobit.knot.gateway.vo.common.OptionItem;
import org.chobit.knot.gateway.vo.common.OptionPage;
import org.chobit.knot.gateway.vo.provider.ProviderProfileItem;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/provider-profiles")
public class ProviderProfileController {

    private final ProviderProfileService providerProfileService;
    private final OptionsService optionsService;

    public ProviderProfileController(ProviderProfileService providerProfileService,
                                     OptionsService optionsService) {
        this.providerProfileService = providerProfileService;
        this.optionsService = optionsService;
    }

    @PostMapping("/list")
    public PageResult<ProviderProfileItem> list(@RequestBody(required = false) PageQuery query) {
        PageQuery actual = query == null
                ? new PageQuery(null, null, null, null, null, null, null, null, null, null, null, null, null, null)
                : query;
        return providerProfileService.list(actual.keyword(), actual.tag(), actual.toPageRequest());
    }

    @GetMapping("/{id}")
    public ProviderProfileItem getById(@PathVariable Long id) {
        return providerProfileService.getById(id);
    }

    /**
     * 供应商信息下拉候选（options）。value=code；{@code kb_providers} 无启用态，disabled 恒 0。
     */
    @PostMapping("/options")
    public OptionPage<OptionItem<Void>> options(@RequestBody(required = false) ProviderProfileOptionQuery query) {
        return optionsService.listProviderProfileOptions(query);
    }

    @GetMapping("/check-code")
    public CodeAvailability checkCode(@RequestParam String code,
                                      @RequestParam(required = false) Long excludeId) {
        return new CodeAvailability(providerProfileService.isCodeAvailable(code, excludeId));
    }

    @OperationLog(module = "provider-profile", operation = "CREATE", entityType = "ProviderProfile",
            entityIdAfter = "#result.id()",
            entityNameAfter = "#result.name()",
            description = "'新建供应商信息'",
            newValueSpel = "@providerProfileService.providerProfileAuditSnapshot(#result.id())")
    @PostMapping
    public ProviderProfileItem create(@RequestBody @Valid ProviderProfileItem request) {
        return providerProfileService.create(request);
    }

    @OperationLog(module = "provider-profile", operation = "UPDATE", entityType = "ProviderProfile",
            entityId = "#p0",
            entityNameAfter = "#result.name()",
            description = "'更新供应商信息'",
                oldValueSpel = "@providerProfileService.providerProfileAuditSnapshot(#p0)",
            newValueSpel = "@providerProfileService.providerProfileAuditSnapshot(#p0)")
    @PutMapping("/{id}")
    public ProviderProfileItem update(@PathVariable Long id,
                                      @RequestBody @Valid ProviderProfileItem request) {
        return providerProfileService.update(id, request);
    }

    @OperationLog(module = "provider-profile", operation = "DELETE", entityType = "ProviderProfile",
            entityId = "#p0",
            entityName = "@providerProfileService.providerProfileAuditSnapshot(#p0)?.get('name')",
            description = "'删除供应商信息'",
            oldValueSpel = "@providerProfileService.providerProfileAuditSnapshot(#p0)")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        providerProfileService.delete(id);
    }
}
