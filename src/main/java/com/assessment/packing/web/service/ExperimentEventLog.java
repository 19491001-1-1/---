package com.assessment.packing.web.service;

import com.assessment.packing.web.dto.ExperimentDtos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 一个实验任务的事件日志，专为 SSE 断线补发服务。
 *
 * <p>每条事件带一个任务内单调递增的序号，随 SSE 的 {@code id:} 字段下发。浏览器 EventSource
 * 自动重连时会把这个序号作为 {@code Last-Event-ID} 请求头带回，服务端据此补发断线期间
 * 漏掉的事件——客户端因此不会看到进度突然从 40% 跳回 0%，也不会永久停在一个中间状态。
 *
 * <p>保留最近 {@link #capacity} 条。超出后丢弃最旧的：真实规模下一次实验最多
 * {@code 500 进度 + 1 完成} 条，容量 600 足以覆盖全程；真触发了裁剪，也是牺牲最老的
 * 进度而不是最新的状态，客户端的最终观感仍然正确。
 *
 * <p>本类不自带锁：调用方（{@code ExperimentTaskService}）在 {@code Job} 上用
 * {@code synchronized} 串行化写入与读取，避免每条事件都做一次 CAS。
 */
final class ExperimentEventLog {
    private final int capacity;
    private final Deque<ExperimentDtos.ProgressEvent> events;
    private long sequence;

    ExperimentEventLog(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("事件日志容量必须为正数：" + capacity);
        this.capacity = capacity;
        this.events = new ArrayDeque<>(capacity);
    }

    /** 追加一条事件并返回它（含分配到的序号）。 */
    ExperimentDtos.ProgressEvent append(String name, Object data) {
        ExperimentDtos.ProgressEvent event =
                new ExperimentDtos.ProgressEvent(++sequence, name, data);
        events.addLast(event);
        while (events.size() > capacity) events.removeFirst();
        return event;
    }

    /** 最近一次分配的序号；尚未记录任何事件时为 0。 */
    long lastSequence() {
        return sequence;
    }

    /**
     * 从持久化的序号继续计数，用于进程重启后的任务恢复。
     *
     * <p>只恢复序号、不恢复事件本身：补发依赖的是「序号大于 N 的事件」，而事件内容在重连时
     * 可以用一份当前快照表达。断线跨过一次重启后，客户端拿到的是一份准确的快照加一个终止事件
     * （若任务已结束），而不是一串过期的中间进度。
     */
    void resumeAt(long sequence) {
        this.sequence = Math.max(this.sequence, sequence);
    }

    /**
     * 补发序号严格大于 {@code after} 的事件，保持原始顺序。
     *
     * <p>{@code after} 为 {@code null} 表示「首次订阅」：此时不补发任何历史事件，
     * 调用方会改为下发一份当前快照。否则新打开的页面会被几百条历史进度逐条刷屏，
     * 最后才落到真实状态。
     */
    List<ExperimentDtos.ProgressEvent> replayAfter(Long after) {
        if (after == null) return List.of();
        List<ExperimentDtos.ProgressEvent> missed = new ArrayList<>();
        for (ExperimentDtos.ProgressEvent event : events) {
            if (event.id() > after) missed.add(event);
        }
        return List.copyOf(missed);
    }

    /**
     * 订阅者是否已经收到过终止事件？用来判断快结束的任务是否还需要补一个 {@code complete}。
     *
     * <p>{@code after} 为 {@code null} 表示首次订阅：此时订阅者什么都没收到过，
     * 因此只要日志里出现过终止事件，就还需要补发一次——否则一个已经结束的任务
     * 只会推出一份「已完成」的快照，却始终不发终止事件，客户端会一直挂着连接。
     */
    boolean hasTerminalAfter(Long after) {
        if (after == null) {
            return events.stream().anyMatch(ExperimentDtos.ProgressEvent::terminal);
        }
        return replayAfter(after).stream().anyMatch(ExperimentDtos.ProgressEvent::terminal);
    }
}
