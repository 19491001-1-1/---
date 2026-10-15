# 贡献指南

感谢参与二维矩形排样实验平台的开发。仓库按可交付产品维护：变更应具备明确的用户价值、稳定契约和可重复验证，不提交个人笔记、课堂材料、临时报告或构建产物。

## 开发环境

- JDK 17+
- Node.js 24.15+
- pnpm 10+
- macOS/Linux 使用 `mvnw` 与 `scripts/*.sh`
- Windows 使用 `mvnw.cmd` 与 `scripts/*.ps1`

## 提交流程

1. 从 `main` 创建短生命周期分支。
2. 保持一次提交只解决一个清晰问题；公共 API、持久化格式或用户界面变化须同步文档。
3. 后端变更运行 `./mvnw clean verify`（Windows：`.\mvnw.cmd clean verify`）。
4. 前端变更在 `frontend/` 运行 `pnpm check` 与 `pnpm build`。
5. 提交前运行 `./scripts/check-repository.sh`（Windows：`.\scripts\check-repository.ps1`）。
6. 创建 Pull Request，说明动机、行为变化、验证结果及兼容性风险。

## 工程约束

- 求解结果必须经过 `SolutionValidator`，不能以算法内部状态代替独立验证。
- 实验必须保持种子、预算和输出顺序可复现；并行优化不得改变结果语义。
- API 错误须返回稳定错误码，不能仅依赖自然语言消息。
- 前端不得绕过统一 API 客户端，也不得把后端受控路径暴露给浏览器输入。
- 新依赖必须有明确必要性，并提交对应锁文件。
- 不提交 `target/`、`out/`、`results/`、`node_modules/`、IDE 配置或本地调试文件。

## 提交信息

建议采用 `type: summary`：`feat`、`fix`、`refactor`、`test`、`docs`、`build`、`ci` 或 `chore`。摘要使用祈使句并准确描述产品变化。

## 报告问题

安全问题不要创建公开 Issue，请按照 [SECURITY.md](SECURITY.md) 的方式私下报告。普通缺陷和功能建议使用仓库模板，并提供最小复现、运行环境和预期行为。
