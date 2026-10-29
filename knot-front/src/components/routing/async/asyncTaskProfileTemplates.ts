/**
 * 异步档案的厂商模板（新建时一键预填，<b>不是</b>事实来源）。
 *
 * <p>⭐ 事实来源永远是库里那一行：模板只是「少打十几个字段」的起点，保存后即成为独立配置。
 * 所以这里可以有厂商知识，但它不参与任何运行时判断——改了档案不会回写模板，反之亦然。</p>
 *
 * <p>⚠ <b>三条口径都是 2026-10-29 实测过的</b>（MVEL 2.5.2，见
 * {@code AsyncTaskExpressionSupportTest}）：</p>
 * <ul>
 *   <li>多值状态写 {@code ['a','b'] contains status}，<b>没有</b> {@code in (...)}；</li>
 *   <li>大小写宽匹配写 {@code status.toLowerCase() == 'x'}——{@code lower(status)} 不存在；</li>
 *   <li>产物是对象数组时，{@code resultPath} 指到数组、{@code resultUrlField} 填字段名，
 *       因为投影语法 {@code videos.![url]} 在此版本不可用。</li>
 * </ul>
 */

export interface AsyncTaskProfileTemplate {
  key: string;
  label: string;
  /** 预填后的档案编码（可改；同协议下建议保持「供应商_用途」的形态） */
  profileCode: string;
  /** 这个模板对应的上游文档口径，一行说明 */
  hint: string;
  values: Record<string, unknown>;
}

export const ASYNC_TASK_PROFILE_TEMPLATES: AsyncTaskProfileTemplate[] = [
  {
    key: "kling-video",
    label: "可灵 Kling（文生视频）",
    profileCode: "KLING_TASK",
    hint: "GET /v1/videos/text2video/{task_id}；task_status 取 succeed/failed；产物在 task_result.videos[].url",
    values: {
      protocol: "VIDEO_GENERATIONS",
      queryMethod: "GET",
      queryPath: "/v1/videos/text2video/{task_id}",
      taskIdPath: "data.task_id",
      statusPath: "data.task_status",
      statusSucceeded: "['succeed','success'] contains status",
      statusFailed: "status == 'failed'",
      statusRunning: "",
      resultPath: "data.task_result.videos",
      resultUrlField: "url",
      errorMessagePath: "data.task_status_msg",
      resultExpiresDays: 30,
      pollIntervalMs: 5000,
      pollBackoffBaseMs: 1000,
      pollBackoffMaxMs: 30000,
      maxAttempts: 360,
      timeoutMs: 1800000,
      remark: "可灵：task_status=succeed/failed，产物 videos[].url（可能多个）"
    }
  },
  {
    key: "ark-video",
    label: "火山方舟 即梦/Seedance（视频生成任务）",
    profileCode: "ARK_VIDEO_TASK",
    hint: "GET /api/v3/contents/generations/tasks/{id}；status 取 succeeded/failed/cancelled；产物在 content.video_url",
    values: {
      protocol: "VIDEO_GENERATIONS",
      queryMethod: "GET",
      queryPath: "/api/v3/contents/generations/tasks/{task_id}",
      taskIdPath: "id",
      statusPath: "status",
      statusSucceeded: "['succeeded'] contains status",
      statusFailed: "['failed','cancelled'] contains status",
      statusRunning: "['queued','running'] contains status",
      resultPath: "content.video_url",
      resultUrlField: "",
      errorMessagePath: "error.message",
      resultExpiresDays: 24,
      pollIntervalMs: 5000,
      pollBackoffBaseMs: 1000,
      pollBackoffMaxMs: 30000,
      maxAttempts: 720,
      timeoutMs: 3600000,
      remark: "方舟：status 四态（queued/running/succeeded/failed），产物 content.video_url"
    }
  },
  {
    key: "generic-single",
    label: "通用骨架（单产物）",
    profileCode: "",
    hint: "只填了占位，请按上游文档替换；适合「提交返回 id、查询返回 status + 单个 url」的接口",
    values: {
      protocol: "VIDEO_GENERATIONS",
      queryMethod: "GET",
      queryPath: "/v1/tasks/{task_id}",
      taskIdPath: "data.id",
      statusPath: "data.status",
      statusSucceeded: "['succeeded','SUCCEEDED'] contains status",
      statusFailed: "['failed','FAILED'] contains status",
      statusRunning: "",
      resultPath: "data.url",
      resultUrlField: "",
      errorMessagePath: "",
      resultExpiresDays: 30,
      pollIntervalMs: 5000,
      pollBackoffBaseMs: 1000,
      pollBackoffMaxMs: 30000,
      maxAttempts: 360,
      timeoutMs: 1800000,
      remark: ""
    }
  }
];
