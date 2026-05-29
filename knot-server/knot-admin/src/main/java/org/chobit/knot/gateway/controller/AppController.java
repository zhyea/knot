package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.annotation.OperationLog;
import org.chobit.knot.gateway.model.PageQuery;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.converter.AppConverter;
import org.chobit.knot.gateway.dto.app.AppDto;
import org.chobit.knot.gateway.service.AppService;
import org.chobit.knot.gateway.vo.app.AppItem;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/apps")
public class AppController {
    private final AppService appService;
    private final AppConverter appConverter;

    public AppController(AppService appService, AppConverter appConverter) {
        this.appService = appService;
        this.appConverter = appConverter;
    }

    @PostMapping("/list")
    public PageResult<AppItem> list(@RequestBody PageQuery query) {
        PageResult<AppDto> page = appService.list(
                query == null ? PageRequest.of(1, 20) : query.toPageRequest(),
                query == null ? null : query.keyword()
        );
        return page.mapList(appConverter::toVOList);
    }

    @OperationLog(module = "app", operation = "CREATE", entityType = "App",
            entityIdAfter = "#result.id()",
            entityNameAfter = "#result.name()",
            description = "'新建应用'",
            recordNewValue = true,
            newValueSpel = "@appService.appAuditSnapshot(#result.id())")
    @PostMapping
    public AppItem create(@RequestBody @Valid AppItem request) {
        AppDto created = appService.create(appConverter.toDto(request));
        return appConverter.toVO(created);
    }

    @OperationLog(module = "app", operation = "UPDATE", entityType = "App",
            entityId = "#p0",
            entityNameAfter = "#result.name()",
            description = "'更新应用'",
            recordOldValue = true,
            oldValueSpel = "@appService.appAuditSnapshot(#p0)",
            recordNewValue = true,
            newValueSpel = "@appService.appAuditSnapshot(#p0)")
    @PutMapping("/{id}")
    public AppItem update(@PathVariable Long id, @RequestBody @Valid AppItem request) {
        AppDto updated = appService.update(id, appConverter.toDto(request));
        return appConverter.toVO(updated);
    }

    @OperationLog(module = "app", operation = "DELETE", entityType = "App",
            entityId = "#p0",
            entityName = "@appService.appAuditSnapshot(#p0)?.get('name')",
            description = "'删除应用'",
            recordOldValue = true,
            oldValueSpel = "@appService.appAuditSnapshot(#p0)")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        appService.delete(id);
    }

}
