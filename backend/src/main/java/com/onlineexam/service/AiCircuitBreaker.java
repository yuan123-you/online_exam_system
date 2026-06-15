package com.onlineexam.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 服务熔断器
 * <p>
 * 为不同功能类型（chat/practice/grade 等）分别跟踪熔断状态，
 * 当连续失败次数达到阈值时自动熔断，经过冷却时间后进入半开状态尝试恢复。
 * </p>
 * <p>
 * 三种状态：
 * <ul>
 *   <li>CLOSED  — 正常状态，允许所有请求通过</li>
 *   <li>OPEN    — 熔断状态，拒绝所有请求</li>
 *   <li>HALF_OPEN — 半开状态，仅允许一个探测请求通过</li>
 * </ul>
 * </p>
 */
@Component
public class AiCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(AiCircuitBreaker.class);

    /** 熔断器状态枚举 */
    public enum State {
        /** 正常状态 */
        CLOSED,
        /** 熔断状态 */
        OPEN,
        /** 半开状态 */
        HALF_OPEN
    }

    /** 每个功能类型对应的熔断状态上下文 */
    private final ConcurrentHashMap<String, CircuitContext> contextMap = new ConcurrentHashMap<>();

    /** 连续失败次数阈值，达到此数值触发熔断 */
    @Value("${ai.circuit-breaker.failure-threshold:5}")
    private int failureThreshold;

    /** 熔断后冷却时间（秒），超时后进入半开状态 */
    @Value("${ai.circuit-breaker.reset-timeout-seconds:30}")
    private int resetTimeoutSeconds;

    @PostConstruct
    public void init() {
        log.info("熔断器初始化完成 - 失败阈值: {}, 冷却时间: {}秒", failureThreshold, resetTimeoutSeconds);
    }

    /**
     * 判断指定功能类型是否允许请求通过
     *
     * @param type 功能类型，如 chat / practice / grade
     * @return true 允许请求，false 拒绝请求
     */
    public boolean allowRequest(String type) {
        CircuitContext ctx = getOrCreateContext(type);

        State currentState = ctx.state;
        switch (currentState) {
            case CLOSED:
                // 正常状态，允许请求
                log.debug("熔断器[{}]状态: CLOSED，允许请求", type);
                return true;

            case OPEN:
                // 熔断状态，检查是否已过冷却时间
                long elapsedSeconds = (System.currentTimeMillis() - ctx.lastFailureTimeMillis) / 1000;
                if (elapsedSeconds >= resetTimeoutSeconds) {
                    // 冷却时间已到，尝试转为半开状态
                    boolean transitioned = ctx.tryTransitionToHalfOpen();
                    if (transitioned) {
                        log.info("熔断器[{}]冷却时间已到({}秒)，从 OPEN 转为 HALF_OPEN，允许探测请求", type, elapsedSeconds);
                        return true;
                    } else {
                        // 并发情况下其他线程已抢先转换，拒绝本次请求
                        log.warn("熔断器[{}]状态: OPEN，冷却时间已到但半开探测位已被占用，拒绝请求", type);
                        return false;
                    }
                } else {
                    log.warn("熔断器[{}]状态: OPEN，冷却剩余 {}秒，拒绝请求", type, resetTimeoutSeconds - elapsedSeconds);
                    return false;
                }

            case HALF_OPEN:
                // 半开状态，仅允许一个探测请求
                boolean allowed = ctx.tryAcquireProbe();
                if (allowed) {
                    log.info("熔断器[{}]状态: HALF_OPEN，允许探测请求通过", type);
                } else {
                    log.warn("熔断器[{}]状态: HALF_OPEN，探测请求已在进行中，拒绝本次请求", type);
                }
                return allowed;

            default:
                log.error("熔断器[{}]未知状态: {}", type, currentState);
                return false;
        }
    }

    /**
     * 记录请求成功
     * <p>
     * 如果当前处于 HALF_OPEN 状态且探测请求成功，则转为 CLOSED 状态；
     * 如果处于 CLOSED 状态，则重置连续失败计数。
     * </p>
     *
     * @param type 功能类型
     */
    public void recordSuccess(String type) {
        CircuitContext ctx = getOrCreateContext(type);
        State currentState = ctx.state;

        if (currentState == State.HALF_OPEN) {
            // 半开状态下探测成功，恢复为正常状态
            ctx.transitionTo(State.CLOSED);
            ctx.failureCount.set(0);
            ctx.probeInFlight.set(false);
            log.info("熔断器[{}]探测请求成功，从 HALF_OPEN 转为 CLOSED，服务恢复正常", type);
        } else if (currentState == State.CLOSED) {
            // 正常状态下成功，重置失败计数
            int previousCount = ctx.failureCount.getAndSet(0);
            if (previousCount > 0) {
                log.info("熔断器[{}]请求成功，连续失败计数从 {} 重置为 0", type, previousCount);
            }
        } else {
            // OPEN 状态下不应出现成功记录，但做防御性处理
            log.warn("熔断器[{}]在 OPEN 状态下收到成功记录，忽略", type);
        }
    }

    /**
     * 记录请求失败
     * <p>
     * 连续失败次数递增，达到阈值时转为 OPEN 状态；
     * 如果处于 HALF_OPEN 状态且探测失败，则重新转为 OPEN 状态。
     * </p>
     *
     * @param type 功能类型
     */
    public void recordFailure(String type) {
        CircuitContext ctx = getOrCreateContext(type);
        State currentState = ctx.state;

        if (currentState == State.HALF_OPEN) {
            // 半开状态下探测失败，重新熔断
            ctx.transitionTo(State.OPEN);
            ctx.lastFailureTimeMillis = System.currentTimeMillis();
            ctx.probeInFlight.set(false);
            log.warn("熔断器[{}]探测请求失败，从 HALF_OPEN 重新转为 OPEN", type);
            return;
        }

        if (currentState == State.CLOSED) {
            int newCount = ctx.failureCount.incrementAndGet();
            ctx.lastFailureTimeMillis = System.currentTimeMillis();
            log.warn("熔断器[{}]请求失败，连续失败次数: {}/{}", type, newCount, failureThreshold);

            if (newCount >= failureThreshold) {
                // 连续失败达到阈值，触发熔断
                ctx.transitionTo(State.OPEN);
                log.error("熔断器[{}]连续失败达到阈值 {}，从 CLOSED 转为 OPEN，熔断生效", type, failureThreshold);
            }
        } else {
            // OPEN 状态下更新失败时间
            ctx.lastFailureTimeMillis = System.currentTimeMillis();
            log.debug("熔断器[{}]在 OPEN 状态下记录失败，更新最后失败时间", type);
        }
    }

    /**
     * 获取指定功能类型的熔断状态信息
     *
     * @param type 功能类型
     * @return 状态信息 Map，包含 state / failureCount / lastFailureTime / failureThreshold / resetTimeoutSeconds
     */
    public Map<String, Object> getCircuitStatus(String type) {
        CircuitContext ctx = getOrCreateContext(type);

        // 如果处于 OPEN 状态，计算冷却剩余时间
        long remainingSeconds = 0;
        if (ctx.state == State.OPEN) {
            long elapsed = (System.currentTimeMillis() - ctx.lastFailureTimeMillis) / 1000;
            remainingSeconds = Math.max(0, resetTimeoutSeconds - elapsed);
        }

        Map<String, Object> status = new java.util.LinkedHashMap<>();
        status.put("state", ctx.state.name());
        status.put("failureCount", ctx.failureCount.get());
        status.put("failureThreshold", failureThreshold);
        status.put("resetTimeoutSeconds", resetTimeoutSeconds);
        status.put("lastFailureTime", ctx.lastFailureTimeMillis > 0
                ? Instant.ofEpochMilli(ctx.lastFailureTimeMillis).toString()
                : "N/A");
        status.put("remainingCooldownSeconds", remainingSeconds);
        return status;
    }

    /**
     * 获取或创建指定功能类型的熔断上下文
     */
    private CircuitContext getOrCreateContext(String type) {
        return contextMap.computeIfAbsent(type, k -> {
            log.info("创建熔断上下文: {}", k);
            return new CircuitContext();
        });
    }

    /**
     * 单个功能类型的熔断状态上下文
     * <p>
     * 使用 volatile 保证状态和时间戳的可见性，
     * 使用 AtomicInteger / AtomicBoolean 保证计数和标记的原子性。
     * </p>
     */
    private static class CircuitContext {

        /** 当前熔断状态，使用 volatile 保证线程可见性 */
        volatile State state = State.CLOSED;

        /** 连续失败次数 */
        final AtomicInteger failureCount = new AtomicInteger(0);

        /** 上次失败的时间戳（毫秒），使用 volatile 保证线程可见性 */
        volatile long lastFailureTimeMillis = 0;

        /** 半开状态下是否已有探测请求在飞行中 */
        final AtomicBoolean probeInFlight = new AtomicBoolean(false);

        /**
         * 尝试从 OPEN 转为 HALF_OPEN
         *
         * @return true 表示转换成功，false 表示其他线程已抢先转换
         */
        boolean tryTransitionToHalfOpen() {
            if (state != State.OPEN) {
                return false;
            }
            synchronized (this) {
                if (state != State.OPEN) {
                    return false;
                }
                state = State.HALF_OPEN;
                probeInFlight.set(false);
                return true;
            }
        }

        /**
         * 尝试获取半开状态下的探测许可
         *
         * @return true 表示获得许可，false 表示已有探测请求在飞行中
         */
        boolean tryAcquireProbe() {
            return probeInFlight.compareAndSet(false, true);
        }

        /**
         * 状态转换（需在外部已做逻辑判断后调用）
         */
        void transitionTo(State newState) {
            synchronized (this) {
                State oldState = this.state;
                this.state = newState;
                log.debug("熔断状态转换: {} -> {}", oldState, newState);
            }
        }
    }
}
