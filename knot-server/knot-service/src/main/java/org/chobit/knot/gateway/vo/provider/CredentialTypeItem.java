package org.chobit.knot.gateway.vo.provider;

import java.util.List;

/**
 * 供应商账户认证类型选项，由 {@link org.chobit.knot.gateway.constants.enums.ProviderCredentialTypeEnum}
 * 单一来源下发。
 *
 * <p>requiredFields 即后端保存时校验的必填字段（见
 * {@code ProviderService#validateCredential}），前端据此动态渲染表单，不再自带映射表。
 */
public record CredentialTypeItem(String code,
                                 String label,
                                 List<String> requiredFields) {
}
