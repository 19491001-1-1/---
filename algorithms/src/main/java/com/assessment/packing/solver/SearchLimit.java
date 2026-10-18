package com.assessment.packing.solver;

/**
 * 元启发式搜索的可选硬限制。最大评价数统计算法实际评价的候选排列；
 * 最大时间从solve开始计时。Long.MAX_VALUE表示不限制。
 */
public record SearchLimit(long maxEvaluations, long maxNanos) {
    public SearchLimit {
        if (maxEvaluations <= 0) throw new IllegalArgumentException("评价上限必须为正数");
        if (maxNanos <= 0) throw new IllegalArgumentException("时间上限必须为正数");
    }

    public static SearchLimit unlimited() {
        return new SearchLimit(Long.MAX_VALUE, Long.MAX_VALUE);
    }

    public static SearchLimit evaluations(long maximum) {
        return new SearchLimit(maximum, Long.MAX_VALUE);
    }

    public static SearchLimit milliseconds(long milliseconds) {
        if (milliseconds <= 0) throw new IllegalArgumentException("毫秒上限必须为正数");
        long nanos = milliseconds > Long.MAX_VALUE / 1_000_000L
                ? Long.MAX_VALUE : milliseconds * 1_000_000L;
        return new SearchLimit(Long.MAX_VALUE, nanos);
    }

    public boolean timeReached(long startNanos) {
        return maxNanos != Long.MAX_VALUE && System.nanoTime() - startNanos >= maxNanos;
    }

    /**
     * 协作式取消：当前线程被请求中断时，搜索应当尽快收尾。
     *
     * <p>求解器在每个循环头部检查该标记，和检查时间预算一样只读一个标志，不会改变搜索语义
     * ——被取消时同样按「返回当前历史最优解」的既有路径收尾。取消是外部请求（例如 Web 层
     * 取消实验任务）施加的，因此放在求解器内部而不是调用方：一次评价可能长达数十毫秒，
     * 只在调用边界检查会让取消按钮迟钝几秒到几分钟。
     *
     * <p>这里刻意用不消费标志的 {@link Thread#isInterrupted()}：取消标记会被循环条件反复求值
     * （外层迭代一次、内层邻域每轮一次），若读走即清除，第一次读到之后标记就消失，循环会
     * 若无其事地继续跑下去。清理由提交任务的线程池负责——每个任务收尾时清一次，
     * 避免把中断状态带给复用的下一个任务。
     */
    public static boolean cancelled() {
        return Thread.currentThread().isInterrupted();
    }
}
