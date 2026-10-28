package org.chobit.knot.gateway.archive;

/**
 * 视频转储：把供应商给的临时下载地址，换成我们可控位置上的长期地址。
 *
 * <p><b>为什么要有这一层</b>：视频产物有两处硬伤——供应商地址<b>带有效期</b>（过期就是 404），
 * 且常在境外、跨域、限速。今天能用是因为交付后立刻就消费掉了；一旦要给客户回看历史、
 * 或者供应商换了域名/下线了模型，旧地址就成了一堆死链。有条件时（S3 / OSS / MinIO）
 * 应该在拿到产物的那一刻就搬到自己这边。</p>
 *
 * <p><b>默认实现是直通</b>（{@link PassthroughVideoArchiveStore}）：没有对象存储时，
 * 供应商地址本来就是唯一可用的地址。这时假装转储（比如复制一份到本地磁盘再对外发）比诚实直连更糟——
 * 它把「我们其实没有归档能力」这件事藏了起来。</p>
 *
 * <h3>实现契约</h3>
 * <ol>
 *   <li><b>必须返回非空白的最终下载地址</b>。返回 null 或空串会被调用方判为失败并回退供应商地址，
 *       等于白搬一次。</li>
 *   <li><b>失败就抛异常，不要自己吞掉返回 null</b>：调用方需要区分「没配转储」和「配了但搬丢了」，
 *       后者要报到 ERROR 日志与告警，前者是正常状态。</li>
 *   <li><b>同一 {@code taskCode} 重复调用应返回同一地址</b>（幂等）。终态重放、手动补搬、
 *       节点在 finish 前宕机后重查，都会让同一个任务被搬第二次——按 {@code taskCode} 定对象键就不会产生副本。</li>
 *   <li><b>实现必须线程安全</b>：轮询器按 {@code concurrency} 并发处理一批任务，同一个实例会被同时调用。</li>
 * </ol>
 *
 * <h3>调用时机与失败代价</h3>
 * <p>只在 {@code AsyncTaskPoller} 的 {@code SETTLE_BILLED} 分支调用一次——也就是「上游说成功且产物齐全」
 * 的那一刻。因此转储<b>不是交付的前置条件</b>：它失败时任务照样落 {@code SUCCEEDED}、照样计费、
 * 客户端照样拿到供应商地址。代价是这条 URL 会随供应商策略失效，日志里留下 ERROR 供事后补搬。</p>
 *
 * <p>⚠ <b>为什么不放 {@code knot-adapter}</b>：转储与厂商协议无关。Kling 之外的厂商、
 * 乃至图片/音频产物，走的都是同一套「临时地址 → 长期地址」。放进某个 adapter 子包，
 * 第二个厂商就会复制一份。</p>
 *
 * <p>实现选择走配置：{@code knot.gateway.video-archive.type}（默认 {@code passthrough}）。
 * 新增 S3 实现时给它 {@code havingValue = "s3"} 且<b>不要</b>写 {@code matchIfMissing}，
 * 两者天然互斥，不需要额外的 catalog 或优先级注解。</p>
 */
public interface VideoArchiveStore {

    /**
     * 转储一个视频，返回客户端可直接下载的最终地址。
     *
     * @param asset 待转储的视频（供应商地址与任务维度）
     * @return 非空白的最终下载地址
     * @throws RuntimeException 转储失败；调用方会回退到 {@link VideoAsset#sourceUrl()} 并记 ERROR
     */
    String archive(VideoAsset asset);
}
