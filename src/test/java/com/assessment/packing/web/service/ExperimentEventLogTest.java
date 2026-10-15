package com.assessment.packing.web.service;

import com.assessment.packing.web.dto.ExperimentDtos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SSE 断线补发的核心：事件序号与重放。
 *
 * <p>这些是「断线重连后进度不能倒退、也不能永久卡住」这条产品承诺的最小可验证单元。
 * 序列逻辑独立成类，正是为了让这几个断言不必启动 Spring 上下文就能精确表达。
 */
final class ExperimentEventLogTest {

    @Test
    void assignsMonotonicSequenceStartingAtOne() {
        ExperimentEventLog log = new ExperimentEventLog(10);

        assertEquals(1, log.append("progress", snapshot(1)).id());
        assertEquals(2, log.append("progress", snapshot(2)).id());
        assertEquals(3, log.append("complete", snapshot(3)).id());
        assertEquals(3, log.lastSequence());
    }

    @Test
    void firstSubscriptionDoesNotReplayHistory() {
        ExperimentEventLog log = new ExperimentEventLog(10);
        for (int index = 0; index < 5; index++) log.append("progress", snapshot(index));

        // null 代表首次订阅：调用方会改发一份当前快照，因此这里必须为空。
        assertTrue(log.replayAfter(null).isEmpty(), "首次订阅不应补发历史事件");
    }

    @Test
    void replaysOnlyEventsAfterTheGivenSequence() {
        ExperimentEventLog log = new ExperimentEventLog(10);
        for (int index = 0; index < 5; index++) log.append("progress", snapshot(index));

        List<ExperimentDtos.ProgressEvent> missed = log.replayAfter(2L);

        assertEquals(3, missed.size(), "只补发 3、4、5 号事件");
        assertEquals(List.of(3L, 4L, 5L), missed.stream().map(ExperimentDtos.ProgressEvent::id).toList());
    }

    @Test
    void replaysNothingWhenSubscriberIsAlreadyUpToDate() {
        ExperimentEventLog log = new ExperimentEventLog(10);
        log.append("progress", snapshot(1));

        assertTrue(log.replayAfter(1L).isEmpty(), "已收到最新事件时不应重复补发");
    }

    @Test
    void dropsOldestEventsButKeepsLatestSequence() {
        ExperimentEventLog log = new ExperimentEventLog(3);
        for (int index = 0; index < 5; index++) log.append("progress", snapshot(index));

        // 只保留最近 3 条（3、4、5 号）；序号不因裁剪而回退，客户端据此判断自己落后多少。
        assertEquals(List.of(3L, 4L, 5L),
                log.replayAfter(0L).stream().map(ExperimentDtos.ProgressEvent::id).toList());
        assertEquals(5, log.lastSequence());
    }

    @Test
    void reportsWhetherATerminalEventWasAlreadyDelivered() {
        ExperimentEventLog log = new ExperimentEventLog(10);
        log.append("progress", snapshot(1));
        log.append("progress", snapshot(2));

        assertFalse(log.hasTerminalAfter(0L), "还没有终止事件");
        log.append("complete", snapshot(3));
        assertTrue(log.hasTerminalAfter(2L), "补发区间里应当含终止事件");
        assertTrue(log.hasTerminalAfter(null), "首次订阅时任务已结束，需要补一个终止事件");
        assertFalse(log.hasTerminalAfter(3L), "终止事件已经收到过了，不必重复下发");
    }

    @Test
    void rejectsNonPositiveCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new ExperimentEventLog(0));
    }

    private static Object snapshot(int mark) {
        return "snapshot-" + mark;
    }
}
