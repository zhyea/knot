package org.chobit.knot.gateway.vo.provider;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 供应商账户认证类型选项，由 {@link org.chobit.knot.gateway.constants.enums.ProviderCredentialTypeEnum}
 * 单一来源下发。
 *
 * <p>requiredFields 是认证字段定义数组（{@code {name,label,type,required,options,default,placeholder}}），
 * 前端据此动态渲染表单（含下拉选项），后端保存校验取其中 required=true 的字段（见
 * {@code ProviderService#validateCredential}）。
 */
public record CredentialTypeItem(String code,
                                 String label,
                                 JsonNode requiredFields) {
}
