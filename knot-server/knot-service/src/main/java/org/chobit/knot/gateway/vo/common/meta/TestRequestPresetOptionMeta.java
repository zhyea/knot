package org.chobit.knot.gateway.vo.common.meta;

/**
 * 测试请求预设下拉候选项的 meta。
 *
 * <p>预设 options 的 {@code value} 是预设主键 {@code id}，接口协议 {@code protocolCode} 决定调试面板
 * 选用哪个协议适配器，是绑定键之外的业务属性，因此落在 meta 而非顶层——改造前它被塞进
 * {@code OptionItem} 的第三构造参数（顶层 {@code code}），语义混杂，此处收敛为明确命名的字段。</p>
 */
public record TestRequestPresetOptionMeta(String protocolCode) {
}
