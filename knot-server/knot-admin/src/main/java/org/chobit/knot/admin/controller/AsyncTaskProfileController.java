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
 * 本类只绑定入参、委托给 {@link AsyncTaskProfileAdminService}，HTTP 状态码由异常体系决定；分页、校验、试跑都在 Service 里。
 * 试跑是独立端点：保存要挡住非法配置，试跑要把错误一次列全展示出来。
 * 路径前缀 {@code /api/system/}，与运行数据 {@code /api/routing/async-tasks} 分开挂，权限也不同。
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
     * 任务协议下拉候选项（模型表单的「任务协议」下拉），用 POST 而非 GET，与其余管理接口保持同一请求形状。
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
     * 返回里 {@code errors} 非空不代表请求失败，HTTP 仍 200，由前端展示给人改。
     */
    @PostMapping("/dry-run")
    public AsyncTaskProfileDryRunVo dryRun(@RequestBody AsyncTaskProfileDryRunDto command) {
        return profileService.dryRun(command);
    }
}
