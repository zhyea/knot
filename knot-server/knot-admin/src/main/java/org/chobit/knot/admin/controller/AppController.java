package org.chobit.knot.admin.controller;

import jakarta.validation.Valid;
import org.chobit.knot.common.annotation.OperationLog;
import org.chobit.knot.svc.converter.AppConverter;
import org.chobit.knot.svc.dto.app.AppDto;
import org.chobit.knot.common.model.PageQuery;
import org.chobit.knot.common.model.PageRequest;
import org.chobit.knot.common.model.PageResult;
import org.chobit.knot.svc.service.AppService;
import org.chobit.knot.svc.vo.app.AppItem;
import org.chobit.knot.common.model.AppOptionQuery;
import org.chobit.knot.svc.service.OptionsService;
import org.chobit.knot.svc.vo.common.OptionItem;
import org.chobit.knot.svc.vo.common.OptionPage;
import org.chobit.knot.svc.vo.common.meta.AppOptionMeta;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/apps")
public class AppController {
    private final AppService appService;
    private final AppConverter appConverter;
    private final OptionsService optionsService;

    /**
     * Constructs a new instance.
     */
    public AppController(AppService appService, AppConverter appConverter, OptionsService optionsService) {
        this.appService = appService;
        this.appConverter = appConverter;
        this.optionsService = optionsService;
    }

    @PostMapping("/options")
    public OptionPage<OptionItem<AppOptionMeta>> listOptions(@RequestBody(required = false) AppOptionQuery query) {
        return optionsService.listAppOptions(query);
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    @PostMapping("/list")
    public PageResult<AppItem> list(@RequestBody PageQuery query) {
        PageResult<AppDto> page = appService.list(
                query == null ? PageRequest.of(1, 20) : query.toPageRequest(),
                query == null ? null : query.keyword()
        );
        return page.mapList(appConverter::toVOList);
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    @OperationLog(module = "app", operation = "CREATE", entityType = "App",
            entityIdAfter = "#result.id()",
            entityNameAfter = "#result.name()",
            description = "'创建应用'",
            newValueSpel = "@appService.appAuditSnapshot(#result.id())")
    @PostMapping
    public AppItem create(@RequestBody @Valid AppItem request) {
        AppDto created = appService.create(appConverter.toDto(request));
        return appConverter.toVO(created);
    }

    /**
     * Updates the target resource. Executes the public operation.
     */
    @OperationLog(module = "app", operation = "UPDATE", entityType = "App",
            entityId = "#p0",
            entityNameAfter = "#result.name()",
            description = "'更新应用'",
            oldValueSpel = "@appService.appAuditSnapshot(#p0)",
            newValueSpel = "@appService.appAuditSnapshot(#p0)")
    @PutMapping("/{id}")
    public AppItem update(@PathVariable Long id, @RequestBody @Valid AppItem request) {
        AppDto updated = appService.update(id, appConverter.toDto(request));
        return appConverter.toVO(updated);
    }

    /**
     * Deletes the target resource. Executes the public operation.
     */
    @OperationLog(module = "app", operation = "DELETE", entityType = "App",
            entityId = "#p0",
            entityName = "@appService.appAuditSnapshot(#p0)?.get('name')",
            description = "'删除应用'",
            oldValueSpel = "@appService.appAuditSnapshot(#p0)")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        appService.delete(id);
    }
}
