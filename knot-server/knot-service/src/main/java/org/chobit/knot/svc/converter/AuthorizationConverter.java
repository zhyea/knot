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
 * 授权管理五类资源的 Entity ↔ 对外载体转换（<b>本模块唯一允许 import 这 5 个实体</b>）。
 *
 * <p>⚠ <b>为什么只有一段 Entity ↔ Item，没有中间 Dto</b>：项目里 Converter 的常规形态是
 * 「Entity ↔ Dto ↔ VO」三段，但那时 Dto 承担<b>语义重命名</b>（如
 * {@code OperationLogEntity.module → OperationLogDto.moduleCode}）。这五类资源的对外字段与
 * 实体逐字同名，硬插一层 Dto 就是纯拷贝的转发壳——规范 §3.1「不留纯转发壳」比「凑齐三段」优先。
 * 等哪天某个字段要对内改名，就在这一段里加 {@code @Mapping}，不必先补 Dto。</p>
 *
 * <p>⭐ 双向方法都得有：这些 Item 同时被当作<b>入参</b>（同 {@code DepartmentItem} 先例），
 * 少了 {@code toEntity} 就得让 controller 伸手去 new 实体，层边界立刻破功。</p>
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
