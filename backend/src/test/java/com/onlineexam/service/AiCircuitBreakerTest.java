package com.onlineexam.service;

import com.onlineexam.service.AiCircuitBreaker.State;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AiCircuitBreakerTest {

    private AiCircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() throws Exception {
        circuitBreaker = new AiCircuitBreaker();
        setField(circuitBreaker, "failureThreshold", 3);
        setField(circuitBreaker, "resetTimeoutSeconds", 2);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getContextMap() throws Exception {
        Field field = circuitBreaker.getClass().getDeclaredField("contextMap");
        field.setAccessible(true);
        return (Map<String, Object>) field.get(circuitBreaker);
    }

    // ================================================================
    // CLOSED state tests
    // ================================================================

    @Nested
    class ClosedStateTests {

        @Test
        void allowRequest_closedState_allowsRequest() {
            assertTrue(circuitBreaker.allowRequest("chat"));
        }

        @Test
        void recordSuccess_closedState_resetsFailureCount() {
            // 先记录2次失败
            circuitBreaker.recordFailure("chat");
            circuitBreaker.recordFailure("chat");

            Map<String, Object> status = circuitBreaker.getCircuitStatus("chat");
            assertEquals(2, status.get("failureCount"));

            // 记录成功后失败计数应重置为0
            circuitBreaker.recordSuccess("chat");

            status = circuitBreaker.getCircuitStatus("chat");
            assertEquals(0, status.get("failureCount"));
            assertEquals("CLOSED", status.get("state"));
        }
    }

    // ================================================================
    // OPEN state tests
    // ================================================================

    @Nested
    class OpenStateTests {

        @Test
        void consecutiveFailures_triggerOpenState() {
            String type = "chat";
            for (int i = 0; i < 3; i++) {
                circuitBreaker.recordFailure(type);
            }

            Map<String, Object> status = circuitBreaker.getCircuitStatus(type);
            assertEquals("OPEN", status.get("state"));
        }

        @Test
        void openState_rejectsRequests() {
            String type = "chat";
            for (int i = 0; i < 3; i++) {
                circuitBreaker.recordFailure(type);
            }

            assertFalse(circuitBreaker.allowRequest(type));
        }

        @Test
        void openState_afterCooldown_transitionsToHalfOpenAndAllowsProbe() throws Exception {
            String type = "chat";
            for (int i = 0; i < 3; i++) {
                circuitBreaker.recordFailure(type);
            }

            // 确认处于 OPEN 状态
            assertEquals("OPEN", circuitBreaker.getCircuitStatus(type).get("state"));

            // 等待冷却时间过去（resetTimeoutSeconds=2）
            Thread.sleep(2100);

            // 应该允许请求，并转为 HALF_OPEN
            assertTrue(circuitBreaker.allowRequest(type));
            assertEquals("HALF_OPEN", circuitBreaker.getCircuitStatus(type).get("state"));
        }

        @Test
        void openState_beforeCooldown_rejectsRequests() throws Exception {
            String type = "chat";
            for (int i = 0; i < 3; i++) {
                circuitBreaker.recordFailure(type);
            }

            // 冷却时间未到，应拒绝请求
            assertFalse(circuitBreaker.allowRequest(type));
            assertEquals("OPEN", circuitBreaker.getCircuitStatus(type).get("state"));
        }
    }

    // ================================================================
    // HALF_OPEN state tests
    // ================================================================

    @Nested
    class HalfOpenStateTests {

        private void transitionToHalfOpen() throws Exception {
            String type = "chat";
            for (int i = 0; i < 3; i++) {
                circuitBreaker.recordFailure(type);
            }
            Thread.sleep(2100);
            assertTrue(circuitBreaker.allowRequest(type));
            assertEquals("HALF_OPEN", circuitBreaker.getCircuitStatus(type).get("state"));
        }

        @Test
        void halfOpen_allowsOnlyOneProbeRequest() throws Exception {
            transitionToHalfOpen();

            // 第一次在 HALF_OPEN 状态下的 allowRequest 会获取探测位，应通过
            assertTrue(circuitBreaker.allowRequest("chat"));
            // 第二次请求探测位已被占用，应被拒绝
            assertFalse(circuitBreaker.allowRequest("chat"));
        }

        @Test
        void halfOpen_probeSuccess_transitionsToClosed() throws Exception {
            transitionToHalfOpen();

            circuitBreaker.recordSuccess("chat");

            Map<String, Object> status = circuitBreaker.getCircuitStatus("chat");
            assertEquals("CLOSED", status.get("state"));
            assertEquals(0, status.get("failureCount"));
        }

        @Test
        void halfOpen_probeFailure_transitionsBackToOpen() throws Exception {
            transitionToHalfOpen();

            circuitBreaker.recordFailure("chat");

            Map<String, Object> status = circuitBreaker.getCircuitStatus("chat");
            assertEquals("OPEN", status.get("state"));
        }
    }

    // ================================================================
    // getCircuitStatus tests
    // ================================================================

    @Nested
    class GetCircuitStatusTests {

        @Test
        void getCircuitStatus_returnsCorrectInfo() {
            String type = "chat";

            circuitBreaker.recordFailure(type);
            circuitBreaker.recordFailure(type);

            Map<String, Object> status = circuitBreaker.getCircuitStatus(type);

            assertEquals("CLOSED", status.get("state"));
            assertEquals(2, status.get("failureCount"));
            assertEquals(3, status.get("failureThreshold"));
            assertEquals(2, status.get("resetTimeoutSeconds"));
            assertNotNull(status.get("lastFailureTime"));
            assertEquals(0L, status.get("remainingCooldownSeconds"));
        }

        @Test
        void getCircuitStatus_openState_showsRemainingCooldown() {
            String type = "chat";
            for (int i = 0; i < 3; i++) {
                circuitBreaker.recordFailure(type);
            }

            Map<String, Object> status = circuitBreaker.getCircuitStatus(type);

            assertEquals("OPEN", status.get("state"));
            long remaining = (long) status.get("remainingCooldownSeconds");
            assertTrue(remaining > 0 && remaining <= 2);
        }
    }

    // ================================================================
    // Independent type tests
    // ================================================================

    @Nested
    class IndependentTypeTests {

        @Test
        void differentTypes_haveIndependentCircuitStates() {
            // chat 触发熔断
            for (int i = 0; i < 3; i++) {
                circuitBreaker.recordFailure("chat");
            }

            // chat 应该是 OPEN
            assertEquals("OPEN", circuitBreaker.getCircuitStatus("chat").get("state"));

            // practice 应该仍然是 CLOSED
            assertEquals("CLOSED", circuitBreaker.getCircuitStatus("practice").get("state"));

            // 允许 practice 请求
            assertTrue(circuitBreaker.allowRequest("practice"));

            // 拒绝 chat 请求
            assertFalse(circuitBreaker.allowRequest("chat"));
        }
    }
}
