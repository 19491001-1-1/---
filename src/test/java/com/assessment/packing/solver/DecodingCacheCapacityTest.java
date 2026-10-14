package com.assessment.packing.solver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 缓存容量开关的解析规则。这条开关是「无缓存 / 固定缓存」对照实验的自变量，
 * 解析错了会把对照组悄悄变成同一组，因此边界值必须钉住。
 */
final class DecodingCacheCapacityTest {

    @AfterEach
    void clearCapacity() {
        System.clearProperty(DecodingCacheCapacity.ENVIRONMENT_VARIABLE);
    }

    @Test
    void defaultsToDefaultCapacityWhenUnset() {
        System.clearProperty(DecodingCacheCapacity.ENVIRONMENT_VARIABLE);

        assertEquals(DecodingCache.DEFAULT_CAPACITY, DecodingCacheCapacity.configured(),
                "未设置开关时应沿用默认容量");
    }

    @Test
    void readsExplicitCapacityIncludingDisabledZero() {
        set("0");
        assertEquals(0, DecodingCacheCapacity.configured(), "0表示关闭缓存");

        set("64");
        assertEquals(64, DecodingCacheCapacity.configured(), "应读取显式容量");

        set("  128  ");
        assertEquals(128, DecodingCacheCapacity.configured(), "应忽略首尾空白");
    }

    @Test
    void treatsBlankAsUnset() {
        set("   ");
        assertEquals(DecodingCache.DEFAULT_CAPACITY, DecodingCacheCapacity.configured(),
                "空白开关应视同未设置");
    }

    @Test
    void rejectsNegativeAndNonNumericValues() {
        set("-1");
        IllegalArgumentException negative = assertThrows(IllegalArgumentException.class,
                DecodingCacheCapacity::configured);
        assertTrue(negative.getMessage().contains("负数"), "报错应说明不能为负数");

        set("abc");
        IllegalArgumentException malformed = assertThrows(IllegalArgumentException.class,
                DecodingCacheCapacity::configured);
        assertTrue(malformed.getMessage().contains("整数"), "报错应说明必须是整数");
    }

    private static void set(String value) {
        System.setProperty(DecodingCacheCapacity.ENVIRONMENT_VARIABLE, value);
    }
}
