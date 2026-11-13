package org.chobit.knot.gateway.archive;

/**
 * 转储：把供应商给的临时下载地址，换成我们可控位置上的长期地址。
 * 实现契约：必须返回非空白的最终地址（null / 空串视为失败并回退供应商地址）；失败抛异常，不要吞成 null。
 * 同一 {@code taskCode} 重复调用返回同一地址（幂等）；实现必须线程安全。
 * 仅在 {@code AsyncTaskRunner} 的 {@code SETTLE_BILLED} 分支调用一次；转储不是交付前置条件，失败时任务照常落 {@code SUCCEEDED} 并计费，客户端仍拿到供应商地址，失败记 ERROR 供事后补搬。
 */
public interface ArchiveStore {

    /**
     * 转储一个产物，返回客户端可直接下载的最终地址。
     *
     * @param asset 待转储的产物（供应商地址与任务维度）
     * @return 非空白的最终下载地址
     * @throws RuntimeException 转储失败；调用方会回退到 {@link ArchiveAsset#sourceUrl()} 并记 ERROR
     */
    String archive(ArchiveAsset asset);
}
