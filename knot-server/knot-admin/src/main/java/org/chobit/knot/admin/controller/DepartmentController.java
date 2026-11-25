package org.chobit.knot.admin.controller;

import jakarta.validation.Valid;
import org.chobit.knot.common.annotation.AuthCheck;
import org.chobit.knot.common.annotation.OperationLog;
import org.chobit.knot.svc.converter.DepartmentConverter;
import org.chobit.knot.svc.dto.system.DepartmentDto;
import org.chobit.knot.common.model.PageQuery;
import org.chobit.knot.common.model.PageResult;
import org.chobit.knot.svc.service.DepartmentService;
import org.chobit.knot.svc.vo.system.DepartmentItem;
import org.chobit.knot.svc.vo.system.DepartmentListItem;
import org.chobit.knot.svc.vo.system.UpdateDepartmentStatusRequest;
import org.chobit.knot.common.model.DepartmentOptionQuery;
import org.chobit.knot.admin.service.support.DepartmentTreeSupport;
import org.chobit.knot.svc.service.OptionsService;
import org.chobit.knot.svc.vo.common.OptionItem;
import org.chobit.knot.svc.vo.common.OptionPage;
import org.chobit.knot.svc.vo.common.meta.DepartmentOptionMeta;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/departments")
@AuthCheck
public class DepartmentController {

    private final DepartmentService departmentService;
    private final DepartmentConverter departmentConverter;
    private final OptionsService optionsService;

    public DepartmentController(DepartmentService departmentService, DepartmentConverter departmentConverter, OptionsService optionsService) {
        this.departmentService = departmentService;
        this.departmentConverter = departmentConverter;
        this.optionsService = optionsService;
    }

    @PostMapping("/options")
    public OptionPage<OptionItem<DepartmentOptionMeta>> listOptions(@RequestBody(required = false) DepartmentOptionQuery query) {
        return optionsService.listDepartmentOptions(query);
    }

    @PostMapping("/list")
    public PageResult<DepartmentListItem> list(@RequestBody(required = false) PageQuery query) {
        PageResult<DepartmentDto> page = departmentService.list(
                PageQuery.toPageRequest(query),
                query == null ? null : query.keyword(),
                query == null ? null : query.parentId()
        );
        return page.mapList(departmentConverter::toListItemVOList);
    }

    @GetMapping("/tree")
    public List<DepartmentItem> tree() {
        return DepartmentTreeSupport.build(departmentConverter.toVOList(departmentService.listAll()));
    }

    @GetMapping("/{id}")
    public DepartmentItem get(@PathVariable Long id) {
        return departmentConverter.toVO(departmentService.getById(id));
    }
    @OperationLog(module = "department", operation = "CREATE", entityType = "Department",
            entityIdAfter = "#result.id()",
            entityNameAfter = "#result.deptName()",
            description = "'创建部门'",
            newValueSpel = "@departmentService.departmentAuditSnapshot(#result.id())")
    @PostMapping
    public DepartmentItem create(@RequestBody @Valid DepartmentItem request) {
        DepartmentDto created = departmentService.create(departmentConverter.toDto(request));
        return departmentConverter.toVO(created);
    }

    @OperationLog(module = "department", operation = "UPDATE", entityType = "Department",
            entityId = "#p0",
            entityNameAfter = "#result.deptName()",
            description = "'更新部门'",
            oldValueSpel = "@departmentService.departmentAuditSnapshot(#p0)",
            newValueSpel = "@departmentService.departmentAuditSnapshot(#p0)")
    @PutMapping("/{id}")
    public DepartmentItem update(@PathVariable Long id, @RequestBody @Valid DepartmentItem request) {
        DepartmentDto updated = departmentService.update(id, departmentConverter.toDto(request));
        return departmentConverter.toVO(updated);
    }

    @OperationLog(module = "department", operation = "UPDATE", entityType = "Department",
            entityId = "#p0",
            entityNameAfter = "#result.deptName()",
            description = "'更新部门状态'",
            oldValueSpel = "@departmentService.departmentAuditSnapshot(#p0)",
            newValueSpel = "@departmentService.departmentAuditSnapshot(#p0)")
    @PutMapping("/{id}/status")
    public DepartmentItem updateStatus(@PathVariable Long id, @RequestBody @Valid UpdateDepartmentStatusRequest request) {
        DepartmentDto updated = departmentService.updateStatus(id, request.status());
        return departmentConverter.toVO(updated);
    }

    @OperationLog(module = "department", operation = "DELETE", entityType = "Department",
            entityId = "#p0",
            entityName = "@departmentService.departmentAuditSnapshot(#p0)['deptName']",
            description = "'删除部门'",
            oldValueSpel = "@departmentService.departmentAuditSnapshot(#p0)")
    @DeleteMapping("/{id}")
    public DepartmentItem delete(@PathVariable Long id) {
        return departmentConverter.toVO(departmentService.deleteReturning(id));
    }
}
