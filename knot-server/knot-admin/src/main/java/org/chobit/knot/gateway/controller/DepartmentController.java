package org.chobit.knot.gateway.controller;

import jakarta.validation.Valid;
import org.chobit.knot.gateway.annotation.OperationLog;
import org.chobit.knot.gateway.converter.DepartmentConverter;
import org.chobit.knot.gateway.dto.system.DepartmentDto;
import org.chobit.knot.gateway.model.PageQuery;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.service.DepartmentService;
import org.chobit.knot.gateway.vo.system.DepartmentItem;
import org.chobit.knot.gateway.vo.system.UpdateDepartmentStatusRequest;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/departments")
public class DepartmentController {

    private final DepartmentService departmentService;
    private final DepartmentConverter departmentConverter;

    public DepartmentController(DepartmentService departmentService, DepartmentConverter departmentConverter) {
        this.departmentService = departmentService;
        this.departmentConverter = departmentConverter;
    }

    @PostMapping("/list")
    public PageResult<DepartmentItem> list(@RequestBody(required = false) PageQuery query) {
        PageResult<DepartmentDto> page = departmentService.list(
                query == null ? PageRequest.of(1, 20) : query.toPageRequest(),
                query == null ? null : query.keyword(),
                query == null ? null : query.parentId()
        );
        return page.mapList(departmentConverter::toVOList);
    }

    @GetMapping("/tree")
    public List<DepartmentItem> tree() {
        return buildTree(departmentConverter.toVOList(departmentService.listAll()));
    }

    @OperationLog(module = "department", operation = "CREATE", entityType = "Department",
            entityIdAfter = "#result.id()",
            entityNameAfter = "#result.deptName()",
            description = "'创建部门'",
            recordNewValue = true,
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
            recordOldValue = true,
            oldValueSpel = "@departmentService.departmentAuditSnapshot(#p0)",
            recordNewValue = true,
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
            recordOldValue = true,
            oldValueSpel = "@departmentService.departmentAuditSnapshot(#p0)",
            recordNewValue = true,
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
            recordOldValue = true,
            oldValueSpel = "@departmentService.departmentAuditSnapshot(#p0)")
    @DeleteMapping("/{id}")
    public DepartmentItem delete(@PathVariable Long id) {
        return departmentConverter.toVO(departmentService.deleteReturning(id));
    }

    private List<DepartmentItem> buildTree(List<DepartmentItem> items) {
        Map<Long, List<DepartmentItem>> childrenByParent = new LinkedHashMap<>();
        for (DepartmentItem item : items) {
            childrenByParent.computeIfAbsent(item.parentId(), key -> new ArrayList<>()).add(item);
        }
        return buildChildren(null, childrenByParent);
    }

    private List<DepartmentItem> buildChildren(Long parentId, Map<Long, List<DepartmentItem>> childrenByParent) {
        List<DepartmentItem> items = childrenByParent.get(parentId);
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<DepartmentItem> result = new ArrayList<>(items.size());
        for (DepartmentItem item : items) {
            DepartmentItem node = new DepartmentItem(
                    item.id(),
                    item.deptCode(),
                    item.deptName(),
                    item.parentId(),
                    item.status(),
                    item.sortOrder(),
                    item.remark(),
                    item.createdAt(),
                    item.updatedAt(),
                    buildChildren(item.id(), childrenByParent)
            );
            result.add(node);
        }
        return result;
    }
}
