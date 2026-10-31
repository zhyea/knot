<template>
  <section class="metric-grid">
    <article
      v-for="metric in metrics"
      :key="metric.key"
      class="metric-card"
      :class="{
        'metric-card--teal': metric.tone === 'teal',
        'metric-card--green': metric.tone === 'green',
        'metric-card--orange': metric.tone === 'orange',
        'metric-card--blue': metric.tone === 'blue'
      }"
      @click="emit('select', metric.route)"
    >
      <div class="metric-card__meta">
        <span class="metric-card__label">{{ metric.label }}</span>
        <el-icon class="metric-card__icon"><component :is="resolveDashboardIcon(metric.iconName)" /></el-icon>
      </div>
      <div class="metric-card__value">{{ formatThousands(metric.value) }}</div>
      <div class="metric-card__hint" :class="{ 'metric-card__hint--active': metric.activityCount > 0 }">
        {{ metric.helperText }}
      </div>
      <div class="metric-card__bars" aria-hidden="true">
        <span
          v-for="bar in metric.distribution"
          :key="bar.key"
          class="metric-card__bar"
          :class="{ 'metric-card__bar--active': bar.active }"
          :style="{ height: `${bar.height}%` }"
        />
      </div>
    </article>
  </section>
</template>

<script setup lang="ts">
import type {PropType} from "vue";
import {formatThousands} from "@/utils/format";
import type {MetricItem} from "@/utils/dashboardMetrics";
import {resolveDashboardIcon} from "./dashboardIcons";

defineProps({
  /** 四张指标卡，比例与文案都由 utils/dashboardMetrics 算好，这里只负责画 */
  metrics: {type: Array as PropType<MetricItem[]>, required: true}
});

const emit = defineEmits(["select"]);
</script>

<style scoped>
.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(240px, 100%), 1fr));
  gap: 16px;
}

.metric-card {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  grid-template-areas:
    "meta bars"
    "value bars"
    "hint bars";
  gap: 10px 16px;
  min-height: 148px;
  padding: 18px 20px;
  border: 1px solid var(--knot-border, #ebeef5);
  background: #fff;
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

.metric-card:hover {
  border-color: #c6d2e1;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.06);
  transform: translateY(-1px);
}

.metric-card__meta {
  grid-area: meta;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
}

.metric-card__label {
  font-size: 15px;
  font-weight: 600;
  color: #7a8a9e;
}

.metric-card__icon {
  width: 34px;
  height: 34px;
  border: 1px solid #e7edf5;
  font-size: 16px;
  color: #4b88d8;
  background: #f7fbff;
}

.metric-card__value {
  grid-area: value;
  font-size: 34px;
  line-height: 1;
  font-weight: 700;
  color: #1f2937;
}

.metric-card__hint {
  grid-area: hint;
  align-self: end;
  font-size: 13px;
  color: #909399;
}

.metric-card__hint--active {
  color: #14b8a6;
}

.metric-card__bars {
  grid-area: bars;
  align-self: stretch;
  display: flex;
  align-items: flex-end;
  gap: 8px;
  min-width: 112px;
  padding-top: 8px;
}

.metric-card__bar {
  flex: 1 1 0;
  min-width: 12px;
  background: #cfe2fb;
}

.metric-card__bar--active {
  background: #4a94e8;
}

.metric-card--teal .metric-card__icon {
  color: #1296a8;
  background: #f0fbfc;
}

.metric-card--teal .metric-card__bar--active {
  background: #23b3c9;
}

.metric-card--green .metric-card__icon {
  color: #2f9e6d;
  background: #f1fbf6;
}

.metric-card--green .metric-card__bar--active {
  background: #56b881;
}

.metric-card--orange .metric-card__icon {
  color: #d97706;
  background: #fff7ed;
}

.metric-card--orange .metric-card__bar--active {
  background: #f59e0b;
}

@media (max-width: 768px) {
  .metric-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .metric-card {
    grid-template-columns: minmax(0, 1fr);
    grid-template-areas:
      "meta"
      "value"
      "hint"
      "bars";
  }

  .metric-card__bars {
    min-width: 0;
    height: 72px;
  }
}
</style>
