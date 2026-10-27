package org.chobit.knot.adapter.usage.extractor;

import org.chobit.knot.common.constants.enums.BillingModeEnum;
import org.chobit.knot.adapter.usage.calculator.BillingModeCalculator;
import org.chobit.knot.adapter.usage.calculator.VideoBillingModeCalculator;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * 通用 VIDEO 用量提取器：{@link AbstractVideoUsageExtractor} 的默认实现。
 *
 * <p>本类只声明身份（编码 / 名称 / 说明 / 计算器 / 计费模式），<b>不覆写任何取数钩子</b>——
 * 通用键表就是基类的默认值。某供应商的字段与通用键表对不上时，另建子类覆写差异钩子；
 * 不要往这里继续堆厂商键，那会让它随接入厂商线性膨胀。</p>
 */
@Component
public class VideoUsageExtractor extends AbstractVideoUsageExtractor {

    public static final String CODE = "VIDEO";
    private final VideoBillingModeCalculator calculator;

    public VideoUsageExtractor(VideoBillingModeCalculator calculator) {
        this.calculator = calculator;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "Video Usage Extractor";
    }

    @Override
    public String description() {
        return "通用视频生成模型提取器：输出时长取响应体 usage 的秒数字段；输入视频时长、输入图片张数、"
                + "像素尺寸、是否有声音（generate_audio / audio 等）、是否有参考视频（video / video_url 等）"
                + "上游响应不回传，全部从请求体解析后随用量带入计价——它们既参与维度分档键，"
                + "也支撑合成量与多分量求和两种业界口径。参考图不计入「有无输入视频」这一维。"
                + "token 分项内嵌保留。仅服务 VIDEO 计费模式。"
                + "字段名对不上的厂商请继承 AbstractVideoUsageExtractor 覆写差异钩子，勿改动本类。";
    }

    @Override
    public BillingModeCalculator calculator() {
        return calculator;
    }

    @Override
    public Set<BillingModeEnum> billingModes() {
        // 时长/像素/声音/参考归 VideoBillingUsage，与 VIDEO 模式一一对应
        return EnumSet.of(BillingModeEnum.VIDEO);
    }
}
