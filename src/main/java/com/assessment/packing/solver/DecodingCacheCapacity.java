package com.assessment.packing.solver;

/**
 * 从环境变量 {@code PACKING_CACHE_CAPACITY} 读取解码缓存容量，用于「无缓存 / 固定缓存 / 自适应缓存」
 * 三种策略的对照实验。
 *
 * <p>默认值 {@link DecodingCache#DEFAULT_CAPACITY} 与此前的硬编码行为逐位一致，因此不设该变量时
 * 既有的运行结果（面积、评价数、布局）完全不变。设置为 {@code 0} 表示关闭缓存：此时
 * {@link DecodingCache} 既不查表也不存条目，每次评价都直接解码。
 *
 * <p>放在求解器包里是为了让所有搜索算法共用同一条读取逻辑，避免各写一份环境变量解析。
 */
public final class DecodingCacheCapacity {
    /** 容量开关的配置名，同时作为系统属性与同名环境变量读取。 */
    public static final String ENVIRONMENT_VARIABLE = "PACKING_CACHE_CAPACITY";

    /** 未设置开关时实际生效的容量，供对照实验显式引用「默认策略」。 */
    public static final int DEFAULT_CAPACITY = DecodingCache.DEFAULT_CAPACITY;

    private DecodingCacheCapacity() {}

    /**
     * 当前配置的缓存容量；未设置时返回默认容量。
     *
     * <p>系统属性优先于环境变量：命令行实验用环境变量，同一次 JVM 内要切换策略的基准与探针
     * 用系统属性。两者都不设时返回 {@link DecodingCache#DEFAULT_CAPACITY}，与既有行为一致。
     */
    public static int configured() {
        String value = System.getProperty(ENVIRONMENT_VARIABLE);
        if (value == null || value.isBlank()) {
            value = System.getenv(ENVIRONMENT_VARIABLE);
        }
        if (value == null || value.isBlank()) return DecodingCache.DEFAULT_CAPACITY;
        try {
            int capacity = Integer.parseInt(value.trim());
            if (capacity < 0) {
                throw new IllegalArgumentException(
                        ENVIRONMENT_VARIABLE + " 不能为负数：" + value);
            }
            return capacity;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    ENVIRONMENT_VARIABLE + " 必须是整数：" + value, e);
        }
    }
}
