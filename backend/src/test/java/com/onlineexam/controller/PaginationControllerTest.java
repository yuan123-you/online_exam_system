package com.onlineexam.controller;

import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import com.onlineexam.repository.QuestionRepository;
import com.onlineexam.service.AuthService;
import com.onlineexam.service.EntityCrudService;
import com.onlineexam.service.PaperService;
import com.onlineexam.service.QuestionService;
import com.onlineexam.service.SubmissionService;
import com.onlineexam.service.SystemLogService;
import com.onlineexam.service.WrongBookService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaginationControllerTest {

    @Mock
    private StoreService storeService;

    @Mock
    private EntityCrudService entityCrudService;

    @Mock
    private AuthService authService;

    @Mock
    private SystemLogService systemLogService;

    @Mock
    private WrongBookService wrongBookService;

    @Mock
    private SubmissionService submissionService;

    @Mock
    private QuestionService questionService;

    @Mock
    private PaperService paperService;

    @Mock
    private QuestionRepository questionRepository;

    @InjectMocks
    private AdminController adminController;

    @InjectMocks
    private WrongBookController wrongBookController;

    @InjectMocks
    private TeacherController teacherController;

    // ================================================================
    // Helper methods
    // ================================================================

    private Store createEmptyStore() {
        Store store = new Store();
        store.departments = new ArrayList<>();
        store.classes = new ArrayList<>();
        store.users = new ArrayList<>();
        store.questions = new ArrayList<>();
        store.papers = new ArrayList<>();
        store.exams = new ArrayList<>();
        store.submissions = new ArrayList<>();
        store.wrongBookEntries = new ArrayList<>();
        store.logs = new ArrayList<>();
        store.backups = new ArrayList<>();
        store.notifications = new ArrayList<>();
        return store;
    }

    private Map<String, Object> makeUser(String id, String role) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", id);
        user.put("role", role);
        user.put("username", id);
        user.put("name", id);
        return user;
    }

    private Map<String, Object> makePageResult(List<Map<String, Object>> rows, int total, int page, int pageSize) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", rows);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> responseBody(ResponseEntity<?> response) {
        return (Map<String, Object>) response.getBody();
    }

    // ================================================================
    // AdminController - UsersPage tests
    // ================================================================

    @Nested
    class AdminUsersPageTests {

        @Test
        void adminCanAccessUsersPage() {
            Store store = createEmptyStore();
            store.users.add(makeUser("admin1", "admin"));
            when(storeService.readStore()).thenReturn(store);
            when(storeService.queryUsersPage(any(), anyInt(), anyInt(), any(), any(), any()))
                    .thenReturn(makePageResult(List.of(), 0, 1, 20));

            ResponseEntity<?> response = adminController.usersPage("admin1", 1, 20, "", "", "", "");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            Map<String, Object> body = responseBody(response);
            assertEquals(0, body.get("total"));
            assertEquals(1, body.get("page"));
            assertEquals(20, body.get("pageSize"));
            verify(storeService).queryUsersPage("", 1, 20, "", "", "");
        }

        @Test
        void nonAdminGetsForbidden() {
            Store store = createEmptyStore();
            store.users.add(makeUser("student1", "student"));
            when(storeService.readStore()).thenReturn(store);

            ResponseEntity<?> response = adminController.usersPage("student1", 1, 20, "", "", "", "");

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            Map<String, Object> body = responseBody(response);
            assertEquals("Forbidden.", body.get("message"));
            verify(storeService, never()).queryUsersPage(any(), anyInt(), anyInt(), any(), any(), any());
        }

        @Test
        void nullUserIdGetsForbidden() {
            Store store = createEmptyStore();
            store.users.add(makeUser("admin1", "admin"));
            when(storeService.readStore()).thenReturn(store);

            ResponseEntity<?> response = adminController.usersPage(null, 1, 20, "", "", "", "");

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        }

        @Test
        void missingUserGetsForbidden() {
            Store store = createEmptyStore();
            when(storeService.readStore()).thenReturn(store);

            ResponseEntity<?> response = adminController.usersPage("nonexistent", 1, 20, "", "", "", "");

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        }

        @Test
        void passesCorrectParametersToQuery() {
            Store store = createEmptyStore();
            store.users.add(makeUser("admin1", "admin"));
            when(storeService.readStore()).thenReturn(store);
            when(storeService.queryUsersPage(eq("teacher"), eq(2), eq(10), eq("张三"), eq("class1"), eq("dept1")))
                    .thenReturn(makePageResult(List.of(), 5, 2, 10));

            ResponseEntity<?> response = adminController.usersPage("admin1", 2, 10, "张三", "teacher", "class1", "dept1");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(storeService).queryUsersPage("teacher", 2, 10, "张三", "class1", "dept1");
        }

        @Test
        void returnsQueryResultDirectly() {
            Store store = createEmptyStore();
            store.users.add(makeUser("admin1", "admin"));
            when(storeService.readStore()).thenReturn(store);
            Map<String, Object> pageResult = makePageResult(List.of(makeUser("u1", "teacher")), 1, 1, 20);
            when(storeService.queryUsersPage(any(), anyInt(), anyInt(), any(), any(), any()))
                    .thenReturn(pageResult);

            ResponseEntity<?> response = adminController.usersPage("admin1", 1, 20, "", "", "", "");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            Map<String, Object> body = responseBody(response);
            assertEquals(1, body.get("total"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) body.get("rows");
            assertEquals(1, rows.size());
        }
    }

    // ================================================================
    // AdminController - LogsPage tests
    // ================================================================

    @Nested
    class AdminLogsPageTests {

        @Test
        void adminCanAccessLogsPage() {
            Store store = createEmptyStore();
            store.users.add(makeUser("admin1", "admin"));
            when(storeService.readStore()).thenReturn(store);
            when(storeService.queryLogsPage(anyInt(), anyInt(), any(), any()))
                    .thenReturn(makePageResult(List.of(), 0, 1, 20));

            ResponseEntity<?> response = adminController.logsPage("admin1", 1, 20, "", "");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            Map<String, Object> body = responseBody(response);
            assertEquals(0, body.get("total"));
            assertEquals(1, body.get("page"));
            assertEquals(20, body.get("pageSize"));
            verify(storeService).queryLogsPage(1, 20, "", "");
        }

        @Test
        void nonAdminGetsForbidden() {
            Store store = createEmptyStore();
            store.users.add(makeUser("teacher1", "teacher"));
            when(storeService.readStore()).thenReturn(store);

            ResponseEntity<?> response = adminController.logsPage("teacher1", 1, 20, "", "");

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            Map<String, Object> body = responseBody(response);
            assertEquals("Forbidden.", body.get("message"));
            verify(storeService, never()).queryLogsPage(anyInt(), anyInt(), any(), any());
        }

        @Test
        void nullUserIdGetsForbidden() {
            Store store = createEmptyStore();
            when(storeService.readStore()).thenReturn(store);

            ResponseEntity<?> response = adminController.logsPage(null, 1, 20, "", "");

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        }

        @Test
        void passesCorrectParametersToQuery() {
            Store store = createEmptyStore();
            store.users.add(makeUser("admin1", "admin"));
            when(storeService.readStore()).thenReturn(store);
            when(storeService.queryLogsPage(eq(3), eq(50), eq("login"), eq("batch")))
                    .thenReturn(makePageResult(List.of(), 10, 3, 50));

            ResponseEntity<?> response = adminController.logsPage("admin1", 3, 50, "login", "batch");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(storeService).queryLogsPage(3, 50, "login", "batch");
        }

        @Test
        void defaultParameterValues() {
            Store store = createEmptyStore();
            store.users.add(makeUser("admin1", "admin"));
            when(storeService.readStore()).thenReturn(store);
            when(storeService.queryLogsPage(eq(1), eq(20), eq(""), eq("")))
                    .thenReturn(makePageResult(List.of(), 0, 1, 20));

            ResponseEntity<?> response = adminController.logsPage("admin1", 1, 20, "", "");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(storeService).queryLogsPage(1, 20, "", "");
        }
    }

    // ================================================================
    // WrongBookController - WrongBookPage tests
    // ================================================================

    @Nested
    class WrongBookPageTests {

        @Test
        void studentCanAccessWrongBookPage() {
            Store store = createEmptyStore();
            store.users.add(makeUser("student1", "student"));
            when(storeService.readStore()).thenReturn(store);
            when(storeService.queryWrongBookPage(eq("student1"), eq(1), eq(20), eq(""), eq("")))
                    .thenReturn(makePageResult(List.of(), 0, 1, 20));

            ResponseEntity<?> response = wrongBookController.wrongBookPage("student1", 1, 20, "", "");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            Map<String, Object> body = responseBody(response);
            assertEquals(0, body.get("total"));
            assertEquals(1, body.get("page"));
            assertEquals(20, body.get("pageSize"));
            verify(storeService).queryWrongBookPage("student1", 1, 20, "", "");
        }

        @Test
        void nonStudentGetsForbidden() {
            Store store = createEmptyStore();
            store.users.add(makeUser("teacher1", "teacher"));
            when(storeService.readStore()).thenReturn(store);

            ResponseEntity<?> response = wrongBookController.wrongBookPage("teacher1", 1, 20, "", "");

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            Map<String, Object> body = responseBody(response);
            assertEquals("Forbidden.", body.get("message"));
            verify(storeService, never()).queryWrongBookPage(any(), anyInt(), anyInt(), any(), any());
        }

        @Test
        void adminGetsForbidden() {
            Store store = createEmptyStore();
            store.users.add(makeUser("admin1", "admin"));
            when(storeService.readStore()).thenReturn(store);

            ResponseEntity<?> response = wrongBookController.wrongBookPage("admin1", 1, 20, "", "");

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        }

        @Test
        void nullUserIdGetsForbidden() {
            Store store = createEmptyStore();
            when(storeService.readStore()).thenReturn(store);

            ResponseEntity<?> response = wrongBookController.wrongBookPage(null, 1, 20, "", "");

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        }

        @Test
        void missingUserGetsForbidden() {
            Store store = createEmptyStore();
            store.users.add(makeUser("student1", "student"));
            when(storeService.readStore()).thenReturn(store);

            ResponseEntity<?> response = wrongBookController.wrongBookPage("nonexistent", 1, 20, "", "");

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        }

        @Test
        void passesCorrectParametersToQuery() {
            Store store = createEmptyStore();
            store.users.add(makeUser("student1", "student"));
            when(storeService.readStore()).thenReturn(store);
            when(storeService.queryWrongBookPage(eq("student1"), eq(2), eq(10), eq("Math"), eq("active")))
                    .thenReturn(makePageResult(List.of(), 3, 2, 10));

            ResponseEntity<?> response = wrongBookController.wrongBookPage("student1", 2, 10, "Math", "active");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(storeService).queryWrongBookPage("student1", 2, 10, "Math", "active");
        }

        @Test
        void defaultParameterValues() {
            Store store = createEmptyStore();
            store.users.add(makeUser("student1", "student"));
            when(storeService.readStore()).thenReturn(store);
            when(storeService.queryWrongBookPage(eq("student1"), eq(1), eq(20), eq(""), eq("")))
                    .thenReturn(makePageResult(List.of(), 0, 1, 20));

            ResponseEntity<?> response = wrongBookController.wrongBookPage("student1", 1, 20, "", "");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(storeService).queryWrongBookPage("student1", 1, 20, "", "");
        }
    }

    // ================================================================
    // TeacherController - QuestionsPage tests
    // ================================================================

    @Nested
    class TeacherQuestionsPageTests {

        @Test
        void teacherCanAccessQuestionsPage() {
            Store store = createEmptyStore();
            store.users.add(makeUser("teacher1", "teacher"));
            when(storeService.readStore()).thenReturn(store);
            when(questionRepository.queryPage(eq("teacher1"), eq(1), eq(20), eq(""), eq("all"), eq("all")))
                    .thenReturn(makePageResult(List.of(), 0, 1, 20));

            ResponseEntity<?> response = teacherController.questionsPage("teacher1", 1, 20, "", "all", "all");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            Map<String, Object> body = responseBody(response);
            assertEquals(0, body.get("total"));
            assertEquals(1, body.get("page"));
            assertEquals(20, body.get("pageSize"));
            verify(questionRepository).queryPage("teacher1", 1, 20, "", "all", "all");
        }

        @Test
        void adminCanAccessQuestionsPage() {
            Store store = createEmptyStore();
            store.users.add(makeUser("admin1", "admin"));
            when(storeService.readStore()).thenReturn(store);
            when(questionRepository.queryPage(isNull(), eq(1), eq(20), eq(""), eq("all"), eq("all")))
                    .thenReturn(makePageResult(List.of(), 0, 1, 20));

            ResponseEntity<?> response = teacherController.questionsPage("admin1", 1, 20, "", "all", "all");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(questionRepository).queryPage(null, 1, 20, "", "all", "all");
        }

        @Test
        void nonTeacherNonAdminGetsForbidden() {
            Store store = createEmptyStore();
            store.users.add(makeUser("student1", "student"));
            when(storeService.readStore()).thenReturn(store);

            ResponseEntity<?> response = teacherController.questionsPage("student1", 1, 20, "", "all", "all");

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            Map<String, Object> body = responseBody(response);
            assertEquals("Forbidden.", body.get("message"));
            verify(questionRepository, never()).queryPage(any(), anyInt(), anyInt(), any(), any(), any());
        }

        @Test
        void nullUserIdGetsForbidden() {
            Store store = createEmptyStore();
            when(storeService.readStore()).thenReturn(store);

            ResponseEntity<?> response = teacherController.questionsPage(null, 1, 20, "", "all", "all");

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        }

        @Test
        void missingUserGetsForbidden() {
            Store store = createEmptyStore();
            store.users.add(makeUser("teacher1", "teacher"));
            when(storeService.readStore()).thenReturn(store);

            ResponseEntity<?> response = teacherController.questionsPage("nonexistent", 1, 20, "", "all", "all");

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        }

        @Test
        void teacherPassesOwnIdAsTeacherId() {
            Store store = createEmptyStore();
            store.users.add(makeUser("teacher1", "teacher"));
            when(storeService.readStore()).thenReturn(store);
            when(questionRepository.queryPage(eq("teacher1"), anyInt(), anyInt(), any(), any(), any()))
                    .thenReturn(makePageResult(List.of(), 0, 1, 20));

            teacherController.questionsPage("teacher1", 1, 20, "", "all", "all");

            verify(questionRepository).queryPage(eq("teacher1"), eq(1), eq(20), eq(""), eq("all"), eq("all"));
        }

        @Test
        void adminPassesNullAsTeacherId() {
            Store store = createEmptyStore();
            store.users.add(makeUser("admin1", "admin"));
            when(storeService.readStore()).thenReturn(store);
            when(questionRepository.queryPage(isNull(), anyInt(), anyInt(), any(), any(), any()))
                    .thenReturn(makePageResult(List.of(), 0, 1, 20));

            teacherController.questionsPage("admin1", 1, 20, "", "all", "all");

            verify(questionRepository).queryPage(isNull(), eq(1), eq(20), eq(""), eq("all"), eq("all"));
        }

        @Test
        void passesCorrectFilterParameters() {
            Store store = createEmptyStore();
            store.users.add(makeUser("teacher1", "teacher"));
            when(storeService.readStore()).thenReturn(store);
            when(questionRepository.queryPage(eq("teacher1"), eq(2), eq(15), eq("微积分"), eq("single"), eq("Math")))
                    .thenReturn(makePageResult(List.of(), 5, 2, 15));

            ResponseEntity<?> response = teacherController.questionsPage("teacher1", 2, 15, "微积分", "single", "Math");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(questionRepository).queryPage("teacher1", 2, 15, "微积分", "single", "Math");
        }

        @Test
        void defaultParameterValues() {
            Store store = createEmptyStore();
            store.users.add(makeUser("teacher1", "teacher"));
            when(storeService.readStore()).thenReturn(store);
            when(questionRepository.queryPage(eq("teacher1"), eq(1), eq(20), eq(""), eq("all"), eq("all")))
                    .thenReturn(makePageResult(List.of(), 0, 1, 20));

            ResponseEntity<?> response = teacherController.questionsPage("teacher1", 1, 20, "", "all", "all");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(questionRepository).queryPage("teacher1", 1, 20, "", "all", "all");
        }
    }
}
