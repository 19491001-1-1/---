# 更新日志

本项目遵循 [Semantic Versioning](https://semver.org/)。

## [Unreleased]

- 算法目录按家族与策略配置组织，五个非 NF 的 Shelf-WM 组合归入默认收起的兼容配置。
- 清楚展示取件、评分、禁忌记忆和 MaxRects-BEST 的组合评价成本；配置数不等于独立算法数。
- 新增 Skyline WM 单变量对照命令，固定取件模式与评分后比较回收收益。
- 十二组完整配置遍历保留逐实例最优结果；Web 冠军按搜索器与解码器组合独立统计，非法结果不参与利用率均值。
- 修正开发代理的 localhost/IPv4 连接歧义，补齐家族筛选和移动端浏览器验证。

## [1.0.0] - 2026-10-15

首个产品化交付版本。

### 新增

- 46 个二维矩形排样算法入口与 14 个默认实验入口。
- Vue 3 操作界面、Spring Boot API、命令行和单 jar 交付方式。
- 单次求解、批量实验、实时进度、取消、缺口重试及跨进程任务恢复。
- CSV、HTML、SVG 实验产物与结果分析视图。
- Java 与前端覆盖率门禁、端到端冒烟测试及 Linux/Windows CI。

### 工程化

- Maven、Node 与 pnpm 版本固定，构建依赖锁定。
- Windows PowerShell 与 macOS/Linux Shell 运维脚本对齐。
- 仓库内容检查、贡献指南、安全策略和 GitHub Issue/PR 模板。

[1.0.0]: https://github.com/wwwwuuuu1122/2D-single-sheet-nesting/tree/v1.0.0
