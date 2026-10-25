package org.chobit.knot.plugin;

/**
 * 插件元数据：插件对自己的全部静态声明，也是 {@code kb_plugin_instances} 派生列的<b>唯一来源</b>。
 *
 * <p><b>为什么 code / name / orderNo 放在这里</b>（2026-10-25 robin 拍板）：这三项原先由
 * {@code data.sql} 手写种子，缺行时再由启动期 reconcile 从能力码<b>猜</b>（拼
 * {@code capability-extension-stage} 当 code、拼 {@code SYNC:capability:stage} 当 name、
 * 取能力码首段拼 plugin_code）。猜出来的值必然和手写值不等价，于是同一列出现两套命名口径，
 * 且长度失控（最长一条 65 字符超过当时的列宽 64，直接把启动打挂）。
 * 元数据本来就是编译期已知的东西，让它随类一起声明，比运行期反推可靠。</p>
 *
 * <p><b>参数语义</b>：</p>
 * <ul>
 *   <li>{@code code} —— 实例业务码，全局唯一，落 {@code kb_plugin_instances.instance_code}。
 *       启动期 reconcile 校验非空、不重复、不超列宽（超长在启动期指名报错，不带病写库）。</li>
 *   <li>{@code name} —— 人类可读名称，落 {@code instance_name}。</li>
 *   <li>{@code capabilityCode} —— <b>能力族</b>码，允许多个插件共享（同一能力的请求/响应/异常
 *       三态各是一个插件实例）。分发器按「能力码 + 扩展点 + 阶段 + 上下文类型」四要素匹配
 *       handler，故不能用实例级的 {@code code} 替代。</li>
 *   <li>{@code extensionPoint} / {@code stageCode} —— 扩展点与执行阶段，分发路由的另两个要素。</li>
 *   <li>{@code orderNo} —— 同一「扩展点 + 阶段」内的执行顺序，升序。表达的是<b>执行语义</b>
 *       （审计日志必须先落、敏感词拦截必须后判），不是运维可调配置，故随代码声明。</li>
 * </ul>
 */
public record PluginDescriptor(String code,
                               String name,
                               String capabilityCode,
                               PluginExtensionPoint extensionPoint,
                               PluginStageCode stageCode,
                               int orderNo) {
}
