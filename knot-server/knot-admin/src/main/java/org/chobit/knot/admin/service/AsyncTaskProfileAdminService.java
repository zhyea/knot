package org.chobit.knot.admin.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import org.chobit.knot.common.model.PageRequest;
import org.chobit.knot.common.model.PageResult;
import org.chobit.knot.svc.dto.async.AsyncTaskProfileDryRunDto;
import org.chobit.knot.svc.dto.async.AsyncTaskProfileQuery;
import org.chobit.knot.svc.dto.async.AsyncTaskProfileSaveDto;
import org.chobit.knot.svc.service.AsyncTaskProfileService;
import org.chobit.knot.svc.vo.async.AsyncTaskProfileDetailVo;
import org.chobit.knot.svc.vo.async.AsyncTaskProfileDryRunVo;
import org.chobit.knot.svc.vo.common.OptionItem;
import org.chobit.knot.svc.vo.common.meta.AsyncTaskProfileMeta;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 异步档案管理面的编排：只有分页 + 委托。
 * 档案的校验、归一、试跑、占用检查全部在 {@link AsyncTaskProfileService} 里。
 * 与 {@link AsyncTaskAdminService} 的差别：本类的写动作直接落库（档案是纯配置，网关每次轮询从库里读），不走网关。
 */
@Service
@RequiredArgsConstructor
public class AsyncTaskProfileAdminService {

    private final AsyncTaskProfileService profileService;

    /** 分页检索（列表页每行带「被几个模型引用」，用于判断能不能删） */
    public PageResult<AsyncTaskProfileDetailVo> list(AsyncTaskProfileQuery query) {
        PageRequest pageRequest = query == null ? PageRequest.of(1, 20) : query.toPageRequest();
        try (Page<?> ignored = PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize())) {
            PageInfo<AsyncTaskProfileDetailVo> pageInfo =
                    new PageInfo<>(profileService.search(query));
            return PageResult.of(pageInfo.getList(), pageInfo.getTotal(),
                    pageRequest.pageNum(), pageRequest.pageSize());
        }
    }

    /** 档案详情 */
    public AsyncTaskProfileDetailVo detail(String profileCode) {
        return profileService.detail(profileCode);
    }

    /** 新建或更新（按编码判断），返回落库后的完整视图 */
    public AsyncTaskProfileDetailVo save(AsyncTaskProfileSaveDto command) {
        return profileService.save(command);
    }

    /** 删除（被引用时拒绝，见 svc 侧注释） */
    public void delete(String profileCode) {
        profileService.delete(profileCode);
    }

    /** 任务协议下拉候选项（模型表单用；只含启用档案） */
    public List<OptionItem<AsyncTaskProfileMeta>> options() {
        return profileService.options();
    }

    /** 试跑：按表达式读两段响应，返回取值/判定/错误清单 */
    public AsyncTaskProfileDryRunVo dryRun(AsyncTaskProfileDryRunDto command) {
        return profileService.dryRun(command);
    }
}
