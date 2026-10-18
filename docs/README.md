# 阅读顺序

项目主线是算法实现和实验比较。前端只辅助查看布局、轨迹与记录。

1. [算法说明](algorithms/README.md)：搜索器、排样引擎与实现差异。
2. [实验运行](experiments/RUNNING.md)：配置、种子、预算与统计层级。
3. [实验结果](experiments/RESULTS.md)：逐实例最好配置、固定配置复验与结论边界。
4. [源码结构](development/ARCHITECTURE.md)：模型、求解、验证与辅助接口。
5. [测试说明](development/TESTING.md)：合法性检查与实现回归。
6. [可视化说明](visualization/RUNNING.md)：需要交互看图时再阅读。

## 文件归属

- `solver/`：搜索器、排样引擎、参数、缓存与统计。
- `optimization/`：组合调参入口与内核适配。
- `experiment/`：算法创建、批量运行与汇总。
- `validation/`：独立布局检查。
- `gcut01/`：原始实例，路径保留以兼容现有配置。
- `config/`、`scripts/`：运行配置与实验脚本。
- `frontend/`、`web/`：辅助查看与实验记录管理，不是算法主体。
- `results/`：本地 CSV、布局、日志和汇报材料，不随 Git 发布。

上述 Java 包位于 `src/main/java/com/assessment/packing/`，测试位于 `src/test/`。新增推导放入 `algorithms/`；实验方案和结果解释放入 `experiments/`。结果摘要应注明源码版本、预算、种子与统计口径。旧文件名保留为链接入口，不维护两份正文。
