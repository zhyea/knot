package org.chobit.knot.admin.controller;

import lombok.RequiredArgsConstructor;
import org.chobit.knot.admin.service.AsyncTaskProfileAdminService;
import org.chobit.knot.common.annotation.OperationLog;
import org.chobit.knot.common.model.PageResult;
import org.chobit.knot.svc.dto.async.AsyncTaskProfileDryRunDto;
import org.chobit.knot.svc.dto.async.AsyncTaskProfileQuery;
import org.chobit.knot.svc.dto.async.AsyncTaskProfileSaveDto;
import org.chobit.knot.svc.vo.async.AsyncTaskProfileDetailVo;
import org.chobit.knot.svc.vo.async.AsyncTaskProfileDryRunVo;
import org.chobit.knot.svc.vo.common.OptionItem;
import org.chobit.knot.svc.vo.common.meta.AsyncTaskProfileMeta;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 异步任务档案（任务协议）管理面：{@code kb_async_task_profiles} 的增删改查与试跑。
 *
 * <p>⭐ <b>本类只做三件事</b>（{@code 10-backend-java.md §3.3}）：绑定入参、委托给
 * {@link AsyncTaskProfileAdminService}、让 HTTP 状态码由异常体系决定。分页、校验、试跑全在 Service 里。</p>
 *
 * <p>⭐ <b>为什么「试跑」是独立端点而不是保存的一部分</b>：保存要挡住非法配置，试跑要把错误
 * <b>展示</b>出来（一次列全，不抛 400）。两者是相反的错误策略，塞进一个端点必然有一方被牺牲。</p>
 *
 * <p>⚠ <b>路径前缀 {@code /api/system/}</b>：档案是「系统级配置」，与 {@code /api/routing/async-tasks}
 * （运行数据）分开挂。两者权限也不同——能看任务不等于能改档案，改错档案会把所有在途任务带偏。</p>
 */
@RestController
@RequestMapping("/api/system/async-task-profiles")
@RequiredArgsConstructor
public class AsyncTaskProfileController {

    private final AsyncTaskProfileAdminService profileService;

    /** 分页检索（keyword / protocol / status） */
    @PostMapping("/list")
    public PageResult<AsyncTaskProfileDetailVo> list(@RequestBody(required = false) AsyncTaskProfileQuery query) {
        return profileService.list(query);
    }

    /**
     * 任务协议下拉候选项（模型表单的「任务协议」下拉）。
     *
     * <p>⚠ 用 POST 而非 GET：它属于管理面 {@code /api/**}，会被拦截器扫到，POST 与其余管理接口
     * 用同一条「请求体 + 无 URL 参数」的习惯，鉴权与审计都只有一种形状。</p>
     */
    @PostMapping("/options")
    public List<OptionItem<AsyncTaskProfileMeta>> options() {
        return profileService.options();
    }

    /** 档案详情（含被引用行数、创建/更新时间） */
    @GetMapping("/{profileCode}")
    public AsyncTaskProfileDetailVo detail(@PathVariable("profileCode") String profileCode) {
        return profileService.detail(profileCode);
    }

    /** 新建或更新（按编码判断；编码与协议在已绑定时不可变） */
    @OperationLog(module = "system", operation = "UPDATE", entityType = "AsyncTaskProfile",
            entityName = "'异步档案 ' + #p0.profileCode()",
            description = "'保存异步任务档案'",
            recordOldValue = false,
            recordNewValue = false)
    @PostMapping("/save")
    public AsyncTaskProfileDetailVo save(@RequestBody AsyncTaskProfileSaveDto command) {
        return profileService.save(command);
    }

    /** 删除（仍被模型引用时拒绝，见 svc 侧说明） */
    @OperationLog(module = "system", operation = "DELETE", entityType = "AsyncTaskProfile",
            entityName = "'异步档案 ' + #p0",
            description = "'删除异步任务档案'",
            recordOldValue = false,
            recordNewValue = false)
    @PostMapping("/delete/{profileCode}")
    public void delete(@PathVariable("profileCode") String profileCode) {
        profileService.delete(profileCode);
    }

    /**
     * 试跑：贴两段上游响应，按当前（未保存的）表达式求值。
     *
     * <p>⚠ 返回里 {@code errors} 非空<b>不代表请求失败</b>（HTTP 仍 200）——那是配置问题，
     * 要展示给人改，而不是让前端弹一个「服务器错误」。</p>
     */
    @PostMapping("/dry-run")
    public AsyncTaskProfileDryRunVo dryRun(@RequestBody AsyncTaskProfileDryRunDto command) {
        return profileService.dryRun(command);
    }
}
