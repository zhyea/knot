package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.annotation.OperationLog;
import org.chobit.knot.gateway.dto.provider.ProviderAccountDto;
import org.chobit.knot.gateway.model.PageQuery;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.converter.ProviderConverter;
import org.chobit.knot.gateway.service.ProviderService;
import org.chobit.knot.gateway.vo.common.CodeAvailability;
import org.chobit.knot.gateway.vo.common.EnabledStatusRequest;
import org.chobit.knot.gateway.vo.provider.AuthApplierItem;
import org.chobit.knot.gateway.vo.provider.CredentialTypeItem;
import org.chobit.knot.gateway.vo.provider.ProviderAccountDetail;
import org.chobit.knot.gateway.vo.provider.ProviderAccountItem;
import org.chobit.knot.gateway.model.ProviderAccountOptionQuery;
import org.chobit.knot.gateway.service.OptionsService;
import org.chobit.knot.gateway.vo.common.OptionItem;
import org.chobit.knot.gateway.vo.common.OptionPage;
import org.chobit.knot.gateway.vo.common.meta.ProviderAccountMeta;
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

    /**
     * Lists matching results. Executes the public operation.
     */
    @PostMapping("/list")
    public PageResult<ProviderAccountItem> list(@RequestBody(required = false) PageQuery query) {
        PageResult<ProviderAccountDto> page = providerService.list(
                query == null ? PageRequest.of(1, 20) : query.toPageRequest(),
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

    /**
     * Returns a suggested value. Executes the public operation.
     */
    @GetMapping("/suggest-code")
    public String suggestCode() {
        return providerService.suggestCode();
    }

    /**
     * Checks whether the requested condition is satisfied. Executes the public operation.
     */
    @GetMapping("/check-code")
    public CodeAvailability checkCode(
            @RequestParam String code,
            @RequestParam(required = false) Long excludeId) {
        return new CodeAvailability(providerService.isCodeAvailable(code, excludeId));
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    @GetMapping("/{id}")
    public ProviderAccountDetail get(@PathVariable Long id) {
        return providerConverter.toDetail(providerService.getById(id));
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
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

    /**
     * Updates the target resource. Executes the public operation.
     */
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

    /**
     * Updates the target resource status. Executes the public operation.
     */
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
