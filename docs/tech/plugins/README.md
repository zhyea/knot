# 插件体系重建设计

本文档用于定义 Knot 新项目形态下的插件体系设计方案。设计目标是不背历史包袱，以网关内核为中心，建立清晰、可治理、可演进的插件机制。

## 文档列表

- [01-插件体系总体设计.md](D:/MyDevelop/JDevelop/workspace/knot/docs/tech/plugins/01-插件体系总体设计.md)
- [02-插件数据模型设计.md](D:/MyDevelop/JDevelop/workspace/knot/docs/tech/plugins/02-插件数据模型设计.md)
- [03-插件执行链与SPI设计.md](D:/MyDevelop/JDevelop/workspace/knot/docs/tech/plugins/03-插件执行链与SPI设计.md)
- [04-插件装载与运行时设计.md](D:/MyDevelop/JDevelop/workspace/knot/docs/tech/plugins/04-插件装载与运行时设计.md)
- [05-插件管理台设计.md](D:/MyDevelop/JDevelop/workspace/knot/docs/tech/plugins/05-插件管理台设计.md)
- [06-实施计划与迁移建议.md](D:/MyDevelop/JDevelop/workspace/knot/docs/tech/plugins/06-实施计划与迁移建议.md)

## 适用范围

- Knot Gateway 运行时扩展
- Knot Admin 插件管理与发布
- 供应商接入、协议桥接、路由策略、通知、定时任务等扩展能力

## 设计原则

1. 内核稳定，插件边界清晰
2. 插件职责单一，不设计万能插件接口
3. 先支持内置插件，再支持外部插件包
4. 默认可观测、可熔断、可限权
5. 管理台管理的是插件实例与绑定关系，不直接耦合代码实现
