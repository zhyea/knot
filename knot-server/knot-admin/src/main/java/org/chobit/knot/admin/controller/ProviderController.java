package org.chobit.knot.admin.controller;

import org.chobit.knot.common.annotation.OperationLog;
import org.chobit.knot.svc.dto.provider.ProviderAccountDto;
import org.chobit.knot.common.model.PageQuery;
import org.chobit.knot.common.model.PageResult;
import org.chobit.knot.svc.converter.ProviderConverter;
import org.chobit.knot.svc.service.ProviderService;
import org.chobit.knot.svc.vo.common.CodeAvailability;
import org.chobit.knot.svc.vo.common.EnabledStatusRequest;
import org.chobit.knot.svc.vo.provider.AuthApplierItem;
import org.chobit.knot.svc.vo.provider.CredentialTypeItem;
import org.chobit.knot.svc.vo.provider.ProviderAccountDetail;
import org.chobit.knot.svc.vo.provider.ProviderAccountItem;
import org.chobit.knot.common.model.ProviderAccountOptionQuery;
import org.chobit.knot.svc.service.OptionsService;
import org.chobit.knot.svc.vo.common.OptionItem;
import org.chobit.knot.svc.vo.common.OptionPage;
import org.chobit.knot.svc.vo.common.meta.ProviderAccountMeta;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/provider-accounts")
public class ProviderController {
    private final ProviderService providerService;
    private final ProviderConverter providerConverter;
    private final OptionsService optionsService;

    /**
     * Constructs a new instance.
     */
    public ProviderController(ProviderService providerService, ProviderConverter providerConverter, OptionsService optionsService) {
        this.providerService = providerService;
        this.providerConverter = providerConverter;
        this.optionsService = optionsService;
    }

    @PostMapping("/options")
    public OptionPage<OptionItem<ProviderAccountMeta>> listOptions(@RequestBody(required = false) ProviderAccountOptionQuery query) {
        return optionsService.listProviderAccountOptions(query);
    }

    @PostMapping("/list")
    public PageResult<ProviderAccountItem> list(@RequestBody(required = false) PageQuery query) {
        PageResult<ProviderAccountDto> page = providerService.list(
                PageQuery.toPageRequest(query),
                query == null ? null : query.keyword(),
                query == null ? null : query.enabled()
        );
        return page.mapList(providerConverter::toVOList);
    }

    /**
     * Lists credential types supported by provider accounts.
     */
    @GetMapping("/credential-types")
    public List<CredentialTypeItem> credentialTypes() {
        return providerService.listCredentialTypes();
    }

    /**
     * Lists auth appliers (auth_applier strategies) supported by provider accounts.
     */
    @GetMapping("/auth-appliers")
    public List<AuthApplierItem> authAppliers() {
        return providerService.listAuthAppliers();
    }

    @GetMapping("/suggest-code")
    public String suggestCode() {
        return providerService.suggestCode();
    }

    @GetMapping("/check-code")
    public CodeAvailability checkCode(
            @RequestParam String code,
            @RequestParam(required = false) Long excludeId) {
        return new CodeAvailability(providerService.isCodeAvailable(code, excludeId));
    }

    @GetMapping("/{id}")
    public ProviderAccountDetail get(@PathVariable Long id) {
        return providerConverter.toDetail(providerService.getById(id));
    }

    @OperationLog(module = "provider-account", operation = "CREATE", entityType = "ProviderAccount",
            entityIdAfter = "#result.id()",
            entityNameAfter = "#result.code()",
            description = "'新建供应商账户'",
            newValueSpel = "@providerService.providerAuditSnapshot(#result.id())")
    @PostMapping
    /**
     * Creates a new resource.
     */
    public ProviderAccountDetail create(@RequestBody @Valid ProviderAccountDetail request) {
        ProviderAccountDto created = providerService.create(providerConverter.toDto(request));
        return providerConverter.toDetail(created);
    }

    @OperationLog(module = "provider-account", operation = "UPDATE", entityType = "ProviderAccount",
            entityId = "#p0",
            entityNameAfter = "#result.code()",
            description = "'更新供应商账户'",
            oldValueSpel = "@providerService.providerAuditSnapshot(#p0)",
            newValueSpel = "@providerService.providerAuditSnapshot(#p0)")
    @PutMapping("/{id}")
    /**
     * Updates the target resource.
     */
    public ProviderAccountDetail update(@PathVariable Long id, @RequestBody @Valid ProviderAccountDetail request) {
        ProviderAccountDto updated = providerService.update(id, providerConverter.toDto(request));
        return providerConverter.toDetail(updated);
    }

    @OperationLog(module = "provider-account", operation = "UPDATE", entityType = "ProviderAccount",
            entityId = "#p0",
            entityNameAfter = "#result.code()",
            description = "'更新供应商账户状态'",
            oldValueSpel = "@providerService.providerAuditSnapshot(#p0)",
            newValueSpel = "@providerService.providerAuditSnapshot(#p0)")
    @PutMapping("/{id}/status")
    public ProviderAccountDetail updateStatus(@PathVariable Long id, @RequestBody @Valid EnabledStatusRequest request) {
        ProviderAccountDto updated = providerService.updateStatus(id, Boolean.TRUE.equals(request.enabled()));
        return providerConverter.toDetail(updated);
    }

}
