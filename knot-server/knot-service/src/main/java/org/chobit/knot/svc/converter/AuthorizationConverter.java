package org.chobit.knot.svc.converter;

import org.chobit.knot.dal.entity.AdminApiPermissionBindingEntity;
import org.chobit.knot.dal.entity.AdminMenuEntity;
import org.chobit.knot.dal.entity.AdminModuleEntity;
import org.chobit.knot.dal.entity.AdminPermissionEntity;
import org.chobit.knot.dal.entity.AdminRoleEntity;
import org.chobit.knot.svc.vo.auth.AdminApiBindingItem;
import org.chobit.knot.svc.vo.auth.AdminMenuDetailItem;
import org.chobit.knot.svc.vo.auth.AdminModuleDetailItem;
import org.chobit.knot.svc.vo.auth.AdminPermissionItem;
import org.chobit.knot.svc.vo.auth.AdminRoleItem;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 授权管理五类资源的 Entity ↔ 对外载体转换（本模块唯一允许 import 这 5 个实体）。
 *
 * 对外字段与实体逐字同名，故不设中间 Dto；这些 Item 同时用作入参，因此双向方法都提供，
 * 避免 controller 直接构造实体。
 */
@Mapper(componentModel = "spring")
public interface AuthorizationConverter {

    // ==================== 模块 ====================

    AdminModuleDetailItem toModuleItem(AdminModuleEntity entity);

    List<AdminModuleDetailItem> toModuleItemList(List<AdminModuleEntity> entities);

    AdminModuleEntity toModuleEntity(AdminModuleDetailItem item);

    // ==================== 菜单 ====================

    AdminMenuDetailItem toMenuItem(AdminMenuEntity entity);

    List<AdminMenuDetailItem> toMenuItemList(List<AdminMenuEntity> entities);

    AdminMenuEntity toMenuEntity(AdminMenuDetailItem item);

    // ==================== 权限 ====================

    AdminPermissionItem toPermissionItem(AdminPermissionEntity entity);

    List<AdminPermissionItem> toPermissionItemList(List<AdminPermissionEntity> entities);

    AdminPermissionEntity toPermissionEntity(AdminPermissionItem item);

    // ==================== API 权限绑定 ====================

    AdminApiBindingItem toApiBindingItem(AdminApiPermissionBindingEntity entity);

    List<AdminApiBindingItem> toApiBindingItemList(List<AdminApiPermissionBindingEntity> entities);

    AdminApiPermissionBindingEntity toApiBindingEntity(AdminApiBindingItem item);

    // ==================== 角色 ====================

    AdminRoleItem toRoleItem(AdminRoleEntity entity);

    List<AdminRoleItem> toRoleItemList(List<AdminRoleEntity> entities);

    AdminRoleEntity toRoleEntity(AdminRoleItem item);
}
