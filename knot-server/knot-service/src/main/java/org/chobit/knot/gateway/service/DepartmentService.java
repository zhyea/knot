package org.chobit.knot.gateway.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.gateway.converter.DepartmentConverter;
import org.chobit.knot.gateway.dto.system.DepartmentDto;
import org.chobit.knot.gateway.entity.DepartmentEntity;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.chobit.knot.gateway.mapper.AppMapper;
import org.chobit.knot.gateway.mapper.DepartmentMapper;
import org.chobit.knot.gateway.mapper.UserMapper;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DepartmentService {

    private final DepartmentMapper departmentMapper;
    private final AppMapper appMapper;
    private final UserMapper userMapper;
    private final DepartmentConverter departmentConverter;

    public DepartmentService(DepartmentMapper departmentMapper,
                             AppMapper appMapper,
                             UserMapper userMapper,
                             DepartmentConverter departmentConverter) {
        this.departmentMapper = departmentMapper;
        this.appMapper = appMapper;
        this.userMapper = userMapper;
        this.departmentConverter = departmentConverter;
    }

    public PageResult<DepartmentDto> list(PageRequest pageRequest, String keyword, Long parentId) {
        PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize());
        PageInfo<DepartmentEntity> pageInfo = new PageInfo<>(departmentMapper.list(normalizeKeyword(keyword), parentId));
        return PageResult.fromPage(pageInfo, departmentConverter::toDtoList, pageRequest);
    }

    public List<DepartmentDto> listAll() {
        return departmentConverter.toDtoList(departmentMapper.listAll());
    }

    public DepartmentDto getById(Long id) {
        DepartmentEntity entity = departmentMapper.getById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "department not found");
        }
        return departmentConverter.toDto(entity);
    }

    @Transactional
    public DepartmentDto create(DepartmentDto request) {
        String deptCode = normalizeRequired(request.deptCode(), "please input department code");
        if (departmentMapper.getByCode(deptCode) != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "department code already exists");
        }
        validateParentForCreate(request.parentId());
        DepartmentEntity entity = departmentConverter.toEntity(request);
        entity.setDeptCode(deptCode);
        entity.setDeptName(normalizeRequired(request.deptName(), "please input department name"));
        entity.setParentId(request.parentId());
        entity.setStatus(request.status() != null ? request.status() : 1);
        entity.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);
        entity.setRemark(normalizeNullable(request.remark()));
        departmentMapper.insert(entity);
        return getById(entity.getId());
    }

    @Transactional
    public DepartmentDto update(Long id, DepartmentDto request) {
        DepartmentEntity existing = departmentMapper.getById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "department not found");
        }
        validateParentForUpdate(id, request.parentId());
        existing.setDeptName(normalizeRequired(request.deptName(), "please input department name"));
        existing.setParentId(request.parentId());
        existing.setStatus(request.status() != null ? request.status() : existing.getStatus());
        existing.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);
        existing.setRemark(normalizeNullable(request.remark()));
        departmentMapper.update(existing);
        return getById(id);
    }

    @Transactional
    public DepartmentDto updateStatus(Long id, Integer status) {
        DepartmentEntity existing = departmentMapper.getById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "department not found");
        }
        existing.setStatus(status != null ? status : existing.getStatus());
        departmentMapper.updateStatus(existing);
        return getById(id);
    }

    @Transactional
    public DepartmentDto deleteReturning(Long id) {
        DepartmentEntity existing = departmentMapper.getById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "department not found");
        }
        Long childCount = departmentMapper.countByParentId(id);
        if (childCount != null && childCount > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "department has child departments and cannot be deleted");
        }
        Long appCount = appMapper.countByDeptId(id);
        if (appCount != null && appCount > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "department is referenced by apps and cannot be deleted");
        }
        Long userCount = userMapper.countByDeptId(id);
        if (userCount != null && userCount > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "department is referenced by users and cannot be deleted");
        }
        departmentMapper.softDelete(id);
        return departmentConverter.toDto(existing);
    }

    public Map<String, Object> departmentAuditSnapshot(Long id) {
        if (id == null) {
            return null;
        }
        DepartmentEntity entity = departmentMapper.getById(id);
        if (entity == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", entity.getId());
        m.put("deptCode", entity.getDeptCode());
        m.put("deptName", entity.getDeptName());
        m.put("parentId", entity.getParentId());
        m.put("status", entity.getStatus());
        m.put("sortOrder", entity.getSortOrder());
        m.put("remark", entity.getRemark());
        m.put("updatedAt", entity.getUpdatedAt());
        return m;
    }

    private static String normalizeKeyword(String keyword) {
        String value = keyword != null ? keyword.trim() : "";
        return value.isEmpty() ? null : value;
    }

    private static String normalizeRequired(String value, String message) {
        String trimmed = value != null ? value.trim() : "";
        if (trimmed.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
        return trimmed;
    }

    private static String normalizeNullable(String value) {
        String trimmed = value != null ? value.trim() : "";
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void validateParentForCreate(Long parentId) {
        if (parentId == null) {
            return;
        }
        DepartmentEntity parent = departmentMapper.getById(parentId);
        if (parent == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "parent department not found");
        }
        if (resolveLevel(parent, loadDepartmentMap()) >= 3) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "department tree supports at most 3 levels");
        }
    }

    private void validateParentForUpdate(Long id, Long parentId) {
        if (parentId == null) {
            validateTreeDepthAfterMove(id, null);
            return;
        }
        if (id.equals(parentId)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "parent department cannot be self");
        }
        Map<Long, DepartmentEntity> entityMap = loadDepartmentMap();
        DepartmentEntity parent = entityMap.get(parentId);
        if (parent == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "parent department not found");
        }
        if (isDescendant(parentId, id, entityMap)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "parent department cannot be current subtree");
        }
        validateTreeDepthAfterMove(id, parentId);
    }

    private void validateTreeDepthAfterMove(Long id, Long parentId) {
        Map<Long, DepartmentEntity> entityMap = loadDepartmentMap();
        int newLevel = parentId == null ? 1 : resolveLevel(entityMap.get(parentId), entityMap) + 1;
        int subtreeHeight = resolveSubtreeHeight(id, entityMap);
        if (newLevel + subtreeHeight - 1 > 3) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "department tree supports at most 3 levels");
        }
    }

    private Map<Long, DepartmentEntity> loadDepartmentMap() {
        List<DepartmentEntity> list = departmentMapper.listAll();
        Map<Long, DepartmentEntity> entityMap = new HashMap<>(list.size());
        for (DepartmentEntity entity : list) {
            entityMap.put(entity.getId(), entity);
        }
        return entityMap;
    }

    private int resolveLevel(DepartmentEntity entity, Map<Long, DepartmentEntity> entityMap) {
        int level = 1;
        Long parentId = entity != null ? entity.getParentId() : null;
        while (parentId != null) {
            DepartmentEntity parent = entityMap.get(parentId);
            if (parent == null) {
                break;
            }
            level++;
            parentId = parent.getParentId();
        }
        return level;
    }

    private int resolveSubtreeHeight(Long id, Map<Long, DepartmentEntity> entityMap) {
        Map<Long, List<Long>> childMap = new HashMap<>();
        for (DepartmentEntity entity : entityMap.values()) {
            if (entity.getParentId() != null) {
                childMap.computeIfAbsent(entity.getParentId(), key -> new ArrayList<>()).add(entity.getId());
            }
        }
        Deque<Long> stack = new ArrayDeque<>();
        Deque<Integer> depthStack = new ArrayDeque<>();
        stack.push(id);
        depthStack.push(1);
        int maxDepth = 1;
        while (!stack.isEmpty()) {
            Long currentId = stack.pop();
            int depth = depthStack.pop();
            maxDepth = Math.max(maxDepth, depth);
            for (Long childId : childMap.getOrDefault(currentId, List.of())) {
                stack.push(childId);
                depthStack.push(depth + 1);
            }
        }
        return maxDepth;
    }

    private boolean isDescendant(Long candidateParentId, Long currentId, Map<Long, DepartmentEntity> entityMap) {
        Long cursor = candidateParentId;
        while (cursor != null) {
            if (cursor.equals(currentId)) {
                return true;
            }
            DepartmentEntity entity = entityMap.get(cursor);
            cursor = entity != null ? entity.getParentId() : null;
        }
        return false;
    }
}
