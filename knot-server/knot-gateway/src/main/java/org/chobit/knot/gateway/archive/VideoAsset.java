package org.chobit.knot.gateway.archive;

import java.time.LocalDateTime;

/**
 * 待转储的视频：供应商临时地址 + 定位这笔任务的维度。
 *
 * <p>⭐ 字段取舍原则是<b>只放调用点真能拿到的</b>。所以这里没有 {@code contentType} /
 * {@code sizeBytes}——查询上游任务状态时拿不到这两个事实，留成字段只会变成一堆永远为 null 的属性，
 * 下一个人要逐个去查「为什么它是空的」。实现方真需要，自己 HEAD 一次去取。</p>
 *
 * <p>也没有 {@code prompt} 之类的请求参数：转储只关心「哪个文件搬到哪」，不关心内容。
 * 真要做内容相关的处理（打水印、转码），那是另一条管线，不该塞进这个接口。</p>
 *
 * @param taskCode            我方任务句柄（{@code kb_async_tasks.task_code}）。同一任务重复转储时拿它做幂等键
 * @param upstreamTaskId      上游任务 ID，排障时与供应商对账用
 * @param sourceUrl           供应商返回的下载地址。<b>通常带有效期</b>，越早搬越安全；也常见跨域与限速
 * @param modelCode           网关模型编码
 * @param upstreamModelCode   上游模型编码
 * @param providerAccountCode 渠道账号编码
 * @param ruleCode            发起这条任务的路由规则（按调用方分目录时用）
 * @param completedAt         任务落终态的时刻，可用于归档路径的日期分区
 */
public record VideoAsset(String taskCode,
                         String upstreamTaskId,
                         String sourceUrl,
                         String modelCode,
                         String upstreamModelCode,
                         String providerAccountCode,
                         String ruleCode,
                         LocalDateTime completedAt) {
}
