# VENDOR_KLING 计费方案（技术方案 + 落地记录）

> 2026-10-26。系列第 2 个厂商定制方案，参照 `VENDOR_MINIMAX`（见 `docs/视频计费规则类型化方案-2026-10-26.md` §8）。
> 价表来源：<https://klingai.com/document-api/pricing/base/video.md>（2026-10 抓取）。

## 1. 官方价表

### 1.1 Kling 4.0（即将上线，按秒）

| 场景 | 计费量（总时长） | 720P | 1080P |
| --- | --- | --- | --- |
| 无输入视频 | 输出时长 | 1.0 | 1.5 |
| 有输入视频（输入 ≤ 输出） | 输出 × 2 | 0.6 | 0.9 |
| 有输入视频（输入 > 输出） | 输入 + 输出 | 0.6 | 0.9 |

输入时长含参考视频与角色时长；Kling 4.0 Flash 价待定。

### 1.2 在线模型（总时长 = 输出时长，按秒）

| 模型 | 分档 | 720P | 1080P | 4K |
| --- | --- | --- | --- | --- |
| 3.0 Turbo | 有声 | 0.8 | 1.0 | — |
| 3.0 | 无声 / 有声 / 动作控制 | 0.6 / 0.9 / 0.9 | 0.8 / 1.2 / 1.2 | 3.0 / 3.0 / — |
| 3.0 Omni | 无参考无声 / 无参考有声 / 有参考无声 | 0.6 / 0.8 / 0.9 | 0.8 / 1.0 / 1.2 | 3.0 / 3.0 / 3.0 |
| O1 | 无参考 / 有参考 | 0.6 / 0.9 | 0.8 / 1.2 | — |
| 2.6 | 无声 / 有声(未指定音色) / 有声(指定音色) / 动作控制 | 0.3 / — / — / 0.5 | 0.5 / 1.0 / 1.2 / 0.8 | — |
| 2.5 Turbo | 无声 | 0.3 | 0.5 | — |

### 1.3 非视频计费项

| 计费项 | 官方价 | 建议模式 | 建议单位 |
| --- | --- | --- | --- |
| 数字人 | 0.4（720P）/ 0.8（1080P）每秒 | VIDEO | PER_SECOND |
| 语音合成 | 0.05 / 次 | REQUEST | PER_REQUEST |
| 对口型 | 0.5 / 5 秒 | VIDEO | PER_SECOND（折 0.1/秒） |
| 人脸识别 | 0.05 / 次 | REQUEST | PER_REQUEST |
| 文生音效 / 视频生音效 | 0.25 / 次 | REQUEST | PER_REQUEST |
| 音色定制 | 0.05 / 次 | REQUEST | PER_REQUEST |
| 图像识别 | 0.1 / 次 | REQUEST | PER_REQUEST |

## 2. 与 MiniMax 的结构性差异（决定了要不要写专属计算方法）

| 维度 | MiniMax（VENDOR_MINIMAX） | Kling（VENDOR_KLING） |
| --- | --- | --- |
| 计费量 | 输出秒 + 输入秒 + 图片张，**三路求和** | 单一计费量（输出秒，或输出+max(输入,输出)） |
| 输入秒 | **独立分量**，按档位输入价另计 | **折进计费量**，绝不另开分量 |
| 「有输入」的表达位置 | 独立分量（`INPUT_VIDEO:档位` 价 / 图片每张价） | **编码在档位单价里**（无输入更贵、有输入更便宜） |
| 音频 | 免费，无附加费 | 有声是**独立档位价**（`:AUDIO`），无附加费 |
| 图片 | 按张计费，前 5 张免费 | 不按张计费 |
| 计算器 | `calculateVendorMiniMax`（三分量） | `calculateVendorKling`（单一分量 + 口径选择） |

结论：两者都需要专属方法，但理由不同——MiniMax 是「分量拆分」，Kling 是「有输入视频的判据 + 口径选择」。

## 3. 后端落地

### 3.1 枚举（`PricingPlanEnum`）

```java
VENDOR_KLING("厂商定制(Kling)", BillingModeEnum.VIDEO);
```

**仅 VIDEO**（与 MiniMax 一致）。理由：非视频项（数字人按秒 / 对口型按秒 / 其余按次）
用 `FIXED` 就能完整表达——它们不需要任何厂商专属算法；把 `VENDOR_KLING` 挂到 REQUEST 上只是换个标签，
却会让「必须显式声明计费量口径」的校验在 REQUEST 规则上无从满足（`validate(plan)` 拿不到计费模式）。

### 3.2 方案层收敛（顺带的删除）

`FixedPricing` 原本被 `VendorMiniMaxPricing` 逐字复制了一份（只有 `plan()` 不同）。
加 Kling 时若继续复制就是第三份，故改为**一个 `FixedPricing` 接收 `plan` 参数**：

```java
private record FixedPricing(PricingPlanEnum plan, PriceSet basePrices,
                            BigDecimal defaultUnitPrice, Map<String, BigDecimal> dimensionPrices)
        implements PricingPlan { /* plan() 返回字段 */ }

// 工厂
case FIXED, VENDOR_MINIMAX, VENDOR_KLING ->
        new FixedPricing(resolved, basePrices, defaultUnitPrice, resolutionPrices);
```

净效果：删掉一个逐字重复的 record，新增一个方案零复制。

### 3.3 校验（`validateVendorKling`）

```java
private String validateVendorKling() {
    if (video == null || blankToNull(video.quantityMode()) == null) {
        return "VENDOR_KLING requires video.quantityMode (OUTPUT for online models, OUTPUT_PLUS_MAX for Kling 4.0)";
    }
    if (VideoConfig.QUANTITY_SUM.equals(video.quantityMode().trim().toUpperCase())) {
        return "VENDOR_KLING does not support video.quantityMode=SUM (...)";
    }
    return null;
}
```

⭐ 为什么口径必须显式：Kling 的档位价把「有无输入视频」编码进单价，而「输入秒要不要折进计费量」
是**另一根轴**，两种口径下同名档位都存在，**无法从价表反推**：

- 静默按 `OUTPUT` → Kling 4.0 少收一半；
- 静默按 `OUTPUT_PLUS_MAX` → 在线模型多收一倍。

`SUM` 被排除：它只在「输入 > 输出」档与 Kling 一致，「输入 ≤ 输出」档官方是**输出×2**而非输出+输入。

### 3.4 计算（`VideoBillingModeCalculator#calculateVendorKling`）

```
总价   = 计费量 × 档位单价
计费量 = 输出秒                        （OUTPUT）
       = 输出 + max(输入, 输出)        （OUTPUT_PLUS_MAX，复用 VideoConfig.quantity）
档位单价 = resolutionPrices[档位] / [档位:AUDIO] / [档位:REFERENCE] / [档位:REFERENCE:AUDIO]
```

两点真实差异（= 方法存在的理由）：

1. **「有输入视频」判据取 `inputDurationSeconds() > 0`，并上请求侧 `reference` 标志。**
   提取器的 `reference` 只认 `input_reference` / `reference_*` 这类请求体键，
   而 Kling 图生视频 / 视频续写传的是 `image` / `video` / `video_url`，一个都命不中；
   沿用通用路径的 `video.reference()` 会让 4.0 的优惠档位价**永远取不到**——
   既按「无输入」的高价计、又乘上折叠后的双倍量，双重多收（单测已锁 `6.0` vs 误算 `10.0`）。
2. **绝不按输入秒 / 输入张另开分量，也不收附加费。** 价表已把「有输入」折进档位价，再开一条就是重复计费。

量纲仍由单位声明（`1M_TOKENS` 取内嵌 token 量，否则按秒并走合成量公式），与通用路径同口径。

### 3.5 其他需要同步的穷举点

| 位置 | 处理 |
| --- | --- |
| `BillingConfig.pricingPlan(...)` 工厂 switch | `case FIXED, VENDOR_MINIMAX, VENDOR_KLING` 合并 |
| `TokenBillingModeCalculator.calculate` switch | 同上合并（否则编译期「未覆盖所有枚举值」） |
| `EnumOptionRegistryTest` | 方案数 4 → 5 + 新增 label 断言（枚举是反射注册，无需改代码） |

## 4. 前端落地

### 4.1 数据契约收口（新模块 `src/utils/billingResolution.ts`）

档位行的创建分散在三处（视频模式组件 / Kling 方案组件 / config_json 回显），
各自写字面量会让新增字段只补到一两处——加 `inputPrice` 时就漏了一处，导致 MiniMax 输入价回显不出来。
故把字段集、`createResolutionRow` 工厂与「后缀键 ↔ 行字段」映射表收进一个模块：

| `resolutionPrices` 键 | 行字段 | 用途 |
| --- | --- | --- |
| `档位` | `price` | 无声价 / 通用单价 |
| `档位:AUDIO` | `audioPrice` | Kling 有声价 |
| `档位:REFERENCE` | `referencePrice` | Kling 含参考价 / 通用「含参考输入」价 |
| `档位:REFERENCE:AUDIO` | `referenceAudioPrice` | Kling 含参考+有声价 |
| `INPUT_VIDEO:档位` | `inputPrice` | MiniMax 输入视频价 |

后缀表按长度降序匹配——`:REFERENCE:AUDIO` 必须先于 `:REFERENCE` 命中，
否则会被截成「档位:AUDIO」，把含参考+有声价错配到有声列。

### 4.2 组件分工

- `plans/PricingPlanVendorKlingConfig.vue`（方案轴，**整表托管**）：5 列档位表
  （档位名 / 无声 / 有声 / 含参考 / 含参考+有声）+ 计费量口径选择 + 非视频项官方价参考表。
- `modes/BillingModeVideoConfig.vue`：新增 `isVendorKling`，在该方案下隐藏自己的档位表、
  计费量口径与输入侧分量三块（只留「默认单价」与 token 量纲提示），避免同一字段被两个组件重复编辑。

### 4.3 序列化规则（`buildBaseConfig`）

- 四维键：`档位` / `档位:REFERENCE` / `档位:AUDIO` / `档位:REFERENCE:AUDIO`，**填了就落库，留空则不下发**
  （留空由后端 `dimensionKeys` 逐级降级取更宽泛的档位价）。
- `video.quantityMode` **连默认 `OUTPUT` 也照写**——这是本方案的核心声明，后端校验要求显式落库。
  其他方案仍然遵守「默认值不写进 JSON」的老规矩（避免默认行为在库里多一份可漂移的副本）。
- 输入侧分量（`inputVideoPerSecond` / `inputImagePerImage` / `freeInputImages`）与 `surcharges` 一律不落。
- 从别的方案带过来的 `SUM` 在切到 `VENDOR_KLING` 时归一成 `OUTPUT`（下拉里也没有 SUM 选项）。

## 5. 门禁结果（2026-10-26）

| 门禁 | 结果 |
| --- | --- |
| `mvn test` knot-common | **132 例全绿**（`BillingConfigTest` 17，含 2 例 Kling） |
| `mvn test` knot-adapter | **275 例全绿**（`VideoBillingModeCalculatorTest` 44，含 7 例 Kling） |
| `mvn test` knot-service | 185 例执行 / **2 例失败**——均在 `EnumConfigCacheTest`（未纳入版本库的既有在途文件，与计费无关，见下） |
| `mvn test` knot-plugin / knot-plugin-impl / knot-dal | 3 / 44 / 4 全绿 |
| `EnumOptionRegistryTest` | 7 例全绿（方案数 5） |
| `vue-tsc --noEmit` | exit 0 |
| `npm run type-check:strict`（`--noUnusedLocals`） | exit 0 |
| `npx vitest run` | 全绿（`BillingRuleFormDialog.test.ts` **35 例**，含 3 例 Kling） |
| `npx vite build --emptyOutDir false` | exit 0 |

### 5.1 两处非本次改动引入的既有问题（已如实处置）

1. `EnumConfigCacheTest.java:118` 编译错：`countItemCodeBindings` 返回 `Long` 而桩写 `thenReturn(0)`。
   该文件 `git status` 为未跟踪（在途新文件）。为跑通门禁做最小修正 `0` → `0L`。
2. 修掉编译后该文件仍有 2 例失败（`createEvictsCache` / `deleteEvictsCache`），
   断言的是 `EnumConfigService` 的缓存失效次数，与计费链路无关，**未改动**——
   属于在途的枚举缓存改造，应在其自身改动内收敛。

## 6. 关键单测（负向保护）

| 用例 | 锁定的错误 |
| --- | --- |
| `vendorKlingShouldDoubleQuantityAndUseCheaperTierWhenInputShorter` | 「有输入」判据误取 `reference` → 10 × 1.0 = 10.0（多收 67%）；正确为 10 × 0.6 = 6.0 |
| `vendorKlingShouldSumInputAndOutputWhenInputLonger` | 只做「输出×2」不判 `输入>输出` → 10 × 0.6；正确为 13 × 0.6 = 7.8 |
| `vendorKlingShouldPreferMostSpecificReferenceAudioTier` | 组合键降级顺序错 → 命中 1.2 或 0.9；正确为 1.5 |
| `vendorKlingShouldNeverBillInputAsSeparateComponent` | 误配 `inputVideoPerSecond` 后另开分量 → 多收一条明细 |
| `vendorKlingShouldNotChargeAnySurcharge` | 沿用通用路径把 `surcharges` 计入 → 多收 0.3 |
| `VENDOR_KLING：config_json 原样往返` | 四维键在回显/保存中丢列（MiniMax 曾静默丢输入价的同类） |

## 7. 遗留

- Kling 4.0 Flash 价待官方公布，出价后只需在档位表加一档、不改代码。
- 非视频项（数字人 / 对口型 / 其余按次）需人工另建规则，官方价已内嵌在方案组件里作参考；
  「对口型 0.5 / 5 秒」必须折算成每秒 0.1 后填写——系统没有「每 5 秒」这个单位基数（与既有「60 不进金额公式」同一条红线）。
