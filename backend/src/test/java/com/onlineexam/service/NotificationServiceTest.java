package com.onlineexam.service;

import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private StoreService storeService;

    @InjectMocks
    private NotificationService notificationService;

    // ================================================================
    // Helper methods
    // ================================================================

    private Store createEmptyStore() {
        Store store = new Store();
        store.notifications = new ArrayList<>();
        store.users = new ArrayList<>();
        return store;
    }

    private Map<String, Object> makeUser(String id, String role, String classId) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", id);
        user.put("role", role);
        user.put("name", "User " + id);
        user.put("classId", classId);
        return user;
    }

    private Map<String, Object> makeNotification(String id, String senderId, String title,
                                                  String targetRole, String targetClassId,
                                                  String targetUserId, boolean isRead,
                                                  String createdAt) {
        Map<String, Object> notif = new LinkedHashMap<>();
        notif.put("id", id);
        notif.put("senderId", senderId);
        notif.put("title", title);
        notif.put("content", "Content of " + title);
        notif.put("type", "general");
        notif.put("targetRole", targetRole);
        notif.put("targetClassId", targetClassId);
        notif.put("targetUserId", targetUserId);
        notif.put("isRead", isRead);
        notif.put("createdAt", createdAt);
        return notif;
    }

    // ================================================================
    // GetUserNotifications Tests
    // ================================================================

    @Nested
    class GetUserNotificationsTests {

        @Test
        void returnsNotificationsTargetingUserDirectly() {
            Store store = createEmptyStore();
            store.users.add(makeUser("u1", "student", "c1"));
            store.notifications.add(makeNotification("n1", "t1", "Direct", "", "", "u1", false, "2025-01-01T00:00:00Z"));
            store.notifications.add(makeNotification("n2", "t1", "Other", "", "", "u2", false, "2025-01-01T00:00:00Z"));
            when(storeService.readStore()).thenReturn(store);

            List<Map<String, Object>> result = notificationService.getUserNotifications("u1");

            assertEquals(1, result.size());
            assertEquals("Direct", result.get(0).get("title"));
        }

        @Test
        void returnsNotificationsForUserRole() {
            Store store = createEmptyStore();
            store.users.add(makeUser("u1", "teacher", ""));
            store.notifications.add(makeNotification("n1", "t1", "Teacher Notif", "teacher", "", "", false, "2025-01-01T00:00:00Z"));
            store.notifications.add(makeNotification("n2", "t1", "Student Notif", "student", "", "", false, "2025-01-01T00:00:00Z"));
            when(storeService.readStore()).thenReturn(store);

            List<Map<String, Object>> result = notificationService.getUserNotifications("u1");

            assertEquals(1, result.size());
            assertEquals("Teacher Notif", result.get(0).get("title"));
        }

        @Test
        void returnsNotificationsForAllRole() {
            Store store = createEmptyStore();
            store.users.add(makeUser("u1", "student", "c1"));
            store.notifications.add(makeNotification("n1", "t1", "All Notif", "all", "", "", false, "2025-01-01T00:00:00Z"));
            store.notifications.add(makeNotification("n2", "t1", "Teacher Only", "teacher", "", "", false, "2025-01-01T00:00:00Z"));
            when(storeService.readStore()).thenReturn(store);

            List<Map<String, Object>> result = notificationService.getUserNotifications("u1");

            assertEquals(1, result.size());
            assertEquals("All Notif", result.get(0).get("title"));
        }

        @Test
        void studentOnlySeesClassSpecificNotificationsForTheirClass() {
            Store store = createEmptyStore();
            store.users.add(makeUser("s1", "student", "c1"));
            store.notifications.add(makeNotification("n1", "t1", "My Class", "student", "c1", "", false, "2025-01-01T00:00:00Z"));
            store.notifications.add(makeNotification("n2", "t1", "Other Class", "student", "c2", "", false, "2025-01-01T00:00:00Z"));
            store.notifications.add(makeNotification("n3", "t1", "All Classes", "student", "", "", false, "2025-01-01T00:00:00Z"));
            when(storeService.readStore()).thenReturn(store);

            List<Map<String, Object>> result = notificationService.getUserNotifications("s1");

            assertEquals(2, result.size());
            List<String> titles = result.stream().map(n -> (String) n.get("title")).toList();
            assertTrue(titles.contains("My Class"));
            assertTrue(titles.contains("All Classes"));
            assertFalse(titles.contains("Other Class"));
        }

        @Test
        void returnsEmptyForUnknownUser() {
            Store store = createEmptyStore();
            store.notifications.add(makeNotification("n1", "t1", "Title", "all", "", "", false, "2025-01-01T00:00:00Z"));
            when(storeService.readStore()).thenReturn(store);

            List<Map<String, Object>> result = notificationService.getUserNotifications("unknown");

            assertTrue(result.isEmpty());
        }

        @Test
        void addsSenderName() {
            Store store = createEmptyStore();
            Map<String, Object> sender = makeUser("t1", "teacher", "");
            sender.put("name", "Teacher Wang");
            store.users.add(sender);
            store.users.add(makeUser("s1", "student", "c1"));
            store.notifications.add(makeNotification("n1", "t1", "Title", "student", "", "", false, "2025-01-01T00:00:00Z"));
            when(storeService.readStore()).thenReturn(store);

            List<Map<String, Object>> result = notificationService.getUserNotifications("s1");

            assertEquals(1, result.size());
            assertEquals("Teacher Wang", result.get(0).get("senderName"));
        }

        @Test
        void addsSystemSenderNameWhenSenderNotFound() {
            Store store = createEmptyStore();
            store.users.add(makeUser("s1", "student", "c1"));
            store.notifications.add(makeNotification("n1", "unknown_sender", "Title", "student", "", "", false, "2025-01-01T00:00:00Z"));
            when(storeService.readStore()).thenReturn(store);

            List<Map<String, Object>> result = notificationService.getUserNotifications("s1");

            assertEquals(1, result.size());
            assertEquals("系统", result.get(0).get("senderName"));
        }

        @Test
        void limitsToFifty() {
            Store store = createEmptyStore();
            store.users.add(makeUser("s1", "student", "c1"));
            for (int i = 0; i < 60; i++) {
                store.notifications.add(makeNotification("n" + i, "t1", "Title " + i, "all", "", "", false,
                        Instant.ofEpochMilli(1000L + i).toString()));
            }
            when(storeService.readStore()).thenReturn(store);

            List<Map<String, Object>> result = notificationService.getUserNotifications("s1");

            assertEquals(50, result.size());
        }
    }

    // ================================================================
    // GetUnreadCount Tests
    // ================================================================

    @Nested
    class GetUnreadCountTests {

        @Test
        void countsOnlyUnreadNotifications() {
            Store store = createEmptyStore();
            store.users.add(makeUser("s1", "student", "c1"));
            store.notifications.add(makeNotification("n1", "t1", "Unread1", "all", "", "", false, "2025-01-01T00:00:00Z"));
            store.notifications.add(makeNotification("n2", "t1", "Read", "all", "", "", true, "2025-01-01T00:00:00Z"));
            store.notifications.add(makeNotification("n3", "t1", "Unread2", "all", "", "", false, "2025-01-01T00:00:00Z"));
            when(storeService.readStore()).thenReturn(store);

            long count = notificationService.getUnreadCount("s1");

            assertEquals(2, count);
        }

        @Test
        void returnsZeroForNoUnread() {
            Store store = createEmptyStore();
            store.users.add(makeUser("s1", "student", "c1"));
            store.notifications.add(makeNotification("n1", "t1", "Read1", "all", "", "", true, "2025-01-01T00:00:00Z"));
            store.notifications.add(makeNotification("n2", "t1", "Read2", "all", "", "", true, "2025-01-01T00:00:00Z"));
            when(storeService.readStore()).thenReturn(store);

            long count = notificationService.getUnreadCount("s1");

            assertEquals(0, count);
        }
    }

    // ================================================================
    // MarkAsRead Tests
    // ================================================================

    @Nested
    class MarkAsReadTests {

        @Test
        void marksNotificationAsRead() {
            Store store = createEmptyStore();
            Map<String, Object> notif = makeNotification("n1", "t1", "Title", "all", "", "", false, "2025-01-01T00:00:00Z");
            store.notifications.add(notif);
            when(storeService.readStore()).thenReturn(store);

            boolean result = notificationService.markAsRead("n1", "s1");

            assertTrue(result);
            assertTrue((Boolean) notif.get("isRead"));
            assertNotNull(notif.get("readAt"));
            verify(storeService).saveRecord(eq("notifications"), eq(notif));
        }

        @Test
        void returnsFalseIfNotFound() {
            Store store = createEmptyStore();
            when(storeService.readStore()).thenReturn(store);

            boolean result = notificationService.markAsRead("nonexistent", "s1");

            assertFalse(result);
            verify(storeService, never()).saveRecord(any(), any());
        }
    }

    // ================================================================
    // MarkAllAsRead Tests
    // ================================================================

    @Nested
    class MarkAllAsReadTests {

        @Test
        void marksAllUnreadAsRead() {
            Store store = createEmptyStore();
            store.users.add(makeUser("s1", "student", "c1"));
            Map<String, Object> n1 = makeNotification("n1", "t1", "Unread1", "all", "", "", false, "2025-01-01T00:00:00Z");
            Map<String, Object> n2 = makeNotification("n2", "t1", "Read", "all", "", "", true, "2025-01-01T00:00:00Z");
            Map<String, Object> n3 = makeNotification("n3", "t1", "Unread2", "all", "", "", false, "2025-01-01T00:00:00Z");
            store.notifications.add(n1);
            store.notifications.add(n2);
            store.notifications.add(n3);
            when(storeService.readStore()).thenReturn(store);

            int count = notificationService.markAllAsRead("s1");

            assertEquals(2, count);
            assertTrue((Boolean) n1.get("isRead"));
            assertTrue((Boolean) n3.get("isRead"));
            verify(storeService, times(2)).saveRecord(eq("notifications"), any());
        }

        @Test
        void returnsCountOfMarked() {
            Store store = createEmptyStore();
            store.users.add(makeUser("s1", "student", "c1"));
            Map<String, Object> n1 = makeNotification("n1", "t1", "Unread", "all", "", "", false, "2025-01-01T00:00:00Z");
            store.notifications.add(n1);
            when(storeService.readStore()).thenReturn(store);

            int count = notificationService.markAllAsRead("s1");

            assertEquals(1, count);
        }
    }

    // ================================================================
    // CreateNotification Tests
    // ================================================================

    @Nested
    class CreateNotificationTests {

        @Test
        void createsNotificationWithCorrectFields() {
            Store store = createEmptyStore();
            when(storeService.readStore()).thenReturn(store);

            Map<String, Object> result = notificationService.createNotification(
                    "t1", "Exam Notice", "Exam tomorrow", "exam", "student", "c1");

            assertEquals("t1", result.get("senderId"));
            assertEquals("Exam Notice", result.get("title"));
            assertEquals("Exam tomorrow", result.get("content"));
            assertEquals("exam", result.get("type"));
            assertEquals("student", result.get("targetRole"));
            assertEquals("c1", result.get("targetClassId"));
            assertEquals("", result.get("targetUserId"));
            assertFalse((Boolean) result.get("isRead"));
            assertNotNull(result.get("id"));
            assertNotNull(result.get("createdAt"));
            verify(storeService).saveRecord(eq("notifications"), eq(result));
        }

        @Test
        void defaultsTypeToGeneralAndTargetClassIdToEmpty() {
            Store store = createEmptyStore();
            when(storeService.readStore()).thenReturn(store);

            Map<String, Object> result = notificationService.createNotification(
                    "t1", "Title", "Content", null, "all", null);

            assertEquals("general", result.get("type"));
            assertEquals("", result.get("targetClassId"));
        }
    }
}
