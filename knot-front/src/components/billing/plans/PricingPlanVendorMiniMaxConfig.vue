<template>
  <div class="vendor-minimax-config">
    <el-alert type="info" :closable="false" class="vendor-minimax-config__formula">
      <template #title>
        <div class="vendor-minimax-config__formula-title">MiniMax H3 多分量计费口径</div>
        <div class="vendor-minimax-config__formula-body">
          总价 = 输出秒数 × 档位输出单价 + 输入秒数 × 档位输入单价
          + max(0, 输入图片张数 − 免费张数) × 每张单价
        </div>
        <div class="vendor-minimax-config__formula-note">
          输入视频单价默认取同档位输出单价（可在上方档位表单独覆盖）；图片单价不随分辨率变化；音频输入免费。
        </div>
      </template>
    </el-alert>

    <el-row :gutter="16">
      <el-col :xs="24" :sm="8">
        <el-form-item label="输入图片 每张" label-width="120px">
          <el-input-number v-model="form.videoInputImagePerImage"
                           :min="0"
                           :step="UNIT_PRICE_STEP"
                           controls-position="right"
                           style="width: 100%"/>
        </el-form-item>
      </el-col>
      <el-col :xs="24" :sm="8">
        <el-form-item label="图片免费张数" label-width="110px">
          <el-input-number v-model="form.videoFreeInputImages"
                           :min="0"
                           :step="1"
                           controls-position="right"
                           style="width: 100%"/>
        </el-form-item>
      </el-col>
      <el-col :xs="24" :sm="8">
        <el-form-item label="音频输入" label-width="80px">
          <span class="vendor-minimax-config__readonly">免费（不设附加费）</span>
        </el-form-item>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import {UNIT_PRICE_STEP} from "@/utils/billingPrice";

/**
 * MiniMax H3 专属计费组件（方案轴：pricingPlan = VENDOR_MINIMAX）。
 *
 * <p>只托管 MiniMax 区别于通用视频规则的两个分量参数：输入图片每张单价、图片免费张数。
 * 输出/输入视频的档位单价在视频模式组件的分辨率表里维护（输入价可在那里单独覆盖），
 * 两处分工避免同一字段被两个组件重复编辑（见 BillingModeVideoConfig 的 isVendorMiniMax 分支）。</p>
 */
defineProps({
  form: {type: Object, required: true}
});
</script>

<style scoped>
.vendor-minimax-config {
  width: 100%;
  margin-top: 4px;
}

.vendor-minimax-config__formula {
  margin-bottom: 12px;
}

.vendor-minimax-config__formula-title {
  font-size: 13px;
  font-weight: 600;
}

.vendor-minimax-config__formula-body {
  margin-top: 4px;
  font-family: var(--el-font-family-monospace, monospace);
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.vendor-minimax-config__formula-note {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.vendor-minimax-config__readonly {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 32px;
}
</style>
