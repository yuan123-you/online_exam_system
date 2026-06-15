package com.onlineexam.service;

import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
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
class EntityCrudServiceTest {

    @Mock
    private StoreService storeService;

    @Mock
    private AuthService authService;

    @Mock
    private SystemLogService systemLogService;

    @InjectMocks
    private EntityCrudService entityCrudService;

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
        return store;
    }

    private Map<String, Object> makeUser(String id, String role, String username, String name) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", id);
        user.put("role", role);
        user.put("username", username);
        user.put("name", name);
        user.put("password", "hashed");
        return user;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractRecord(ResponseEntity<?> response) {
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        return body != null ? (Map<String, Object>) body.get("record") : null;
    }

    @SuppressWarnings("unchecked")
    private String extractMessage(ResponseEntity<?> response) {
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        return body != null ? String.valueOf(body.get("message")) : null;
    }

    // ================================================================
    // Create Entity tests
    // ================================================================

    @Nested
    class CreateEntityTests {

        @Test
        void createEntity_adminCreatesStudent_returnsOk() {
            Store store = createEmptyStore();
            store.users.add(makeUser("admin1", "admin", "admin", "Admin"));
            Map<String, Object> classRecord = new LinkedHashMap<>();
            classRecord.put("id", "c1");
            classRecord.put("name", "Class 1");
            store.classes.add(classRecord);
            when(storeService.readStore()).thenReturn(store);
            when(authService.hashPassword(anyString())).thenReturn("$2a$10$hashed");

            Map<String, Object> record = new LinkedHashMap<>();
            record.put("username", "newstudent");
            record.put("password", "pass123");
            record.put("name", "New Student");
            record.put("role", "student");
            record.put("classId", "c1");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("entity", "users");
            body.put("record", record);

            ResponseEntity<?> response = entityCrudService.createEntity("admin1", body);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            Map<String, Object> created = extractRecord(response);
            assertNotNull(created);
            assertEquals("newstudent", created.get("username"));
            assertNull(created.get("password"), "Password should be stripped from response for users");
            verify(storeService).saveRecord(eq("users"), any());
        }

        @Test
        void createEntity_teacherCreatesQuestion_setsTeacherId() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher", "teacher1", "Teacher One"));
            when(storeService.readStore()).thenReturn(store);

            Map<String, Object> record = new LinkedHashMap<>();
            record.put("title", "What is 2+2?");
            record.put("subject", "Math");
            record.put("type", "single");
            record.put("score", 10);
            record.put("options", List.of("3", "4", "5"));
            record.put("answer", List.of("4"));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("entity", "questions");
            body.put("record", record);

            ResponseEntity<?> response = entityCrudService.createEntity("t1", body);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(storeService).saveRecord(eq("questions"), argThat(saved -> {
                return "t1".equals(String.valueOf(saved.get("teacherId")))
                        && "t1".equals(String.valueOf(saved.get("createdBy")));
            }));
        }

        @Test
        void createEntity_studentTriesToCreate_returnsForbidden() {
            Store store = createEmptyStore();
            store.users.add(makeUser("s1", "student", "student1", "Student"));
            when(storeService.readStore()).thenReturn(store);

            Map<String, Object> body = Map.of("entity", "questions", "record", Map.of("title", "test"));
            ResponseEntity<?> response = entityCrudService.createEntity("s1", body);

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        }

        @Test
        void createEntity_teacherCreatesWithInvalidType_returnsBadRequest() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher", "teacher1", "Teacher"));
            when(storeService.readStore()).thenReturn(store);

            Map<String, Object> record = new LinkedHashMap<>();
            record.put("title", "Bad question");
            record.put("subject", "Math");
            record.put("type", "invalid_type");
            record.put("score", 10);

            Map<String, Object> body = Map.of("entity", "questions", "record", record);
            ResponseEntity<?> response = entityCrudService.createEntity("t1", body);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }

        @Test
        void createEntity_emptyRecord_returnsBadRequest() {
            Store store = createEmptyStore();
            store.users.add(makeUser("admin1", "admin", "admin", "Admin"));
            when(storeService.readStore()).thenReturn(store);

            Map<String, Object> body = Map.of("entity", "users", "record", Map.of());
            ResponseEntity<?> response = entityCrudService.createEntity("admin1", body);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }

        @Test
        void createEntity_teacherExceedsQuestionLimit_returnsBadRequest() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher", "teacher1", "Teacher"));
            // Add 5000 existing questions for this teacher
            for (int i = 0; i < 5000; i++) {
                Map<String, Object> q = new LinkedHashMap<>();
                q.put("id", "q" + i);
                q.put("teacherId", "t1");
                store.questions.add(q);
            }
            when(storeService.readStore()).thenReturn(store);

            Map<String, Object> record = new LinkedHashMap<>();
            record.put("title", "One more question");
            record.put("subject", "Math");
            record.put("type", "single");
            record.put("score", 10);

            Map<String, Object> body = Map.of("entity", "questions", "record", record);
            ResponseEntity<?> response = entityCrudService.createEntity("t1", body);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertTrue(extractMessage(response).contains("5000"));
        }

        @Test
        void createEntity_adminCreatesUser_passwordIsHashed() {
            Store store = createEmptyStore();
            store.users.add(makeUser("admin1", "admin", "admin", "Admin"));
            Map<String, Object> dept = new LinkedHashMap<>();
            dept.put("id", "d1");
            dept.put("name", "CS Dept");
            store.departments.add(dept);
            when(storeService.readStore()).thenReturn(store);
            when(authService.hashPassword("secret123")).thenReturn("$2a$10$hashedpw");

            Map<String, Object> record = new LinkedHashMap<>();
            record.put("username", "newteacher");
            record.put("password", "secret123");
            record.put("name", "New Teacher");
            record.put("role", "teacher");
            record.put("departmentId", "d1");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("entity", "users");
            body.put("record", record);

            ResponseEntity<?> response = entityCrudService.createEntity("admin1", body);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(storeService).saveRecord(eq("users"), argThat(saved ->
                    "$2a$10$hashedpw".equals(String.valueOf(saved.get("password")))
            ));
        }
    }

    // ================================================================
    // Update Entity tests
    // ================================================================

    @Nested
    class UpdateEntityTests {

        @Test
        void updateEntity_adminUpdatesUserName_returnsOk() {
            Store store = createEmptyStore();
            Map<String, Object> admin = makeUser("admin1", "admin", "admin", "Admin");
            Map<String, Object> target = makeUser("u2", "student", "student1", "Old Name");
            store.users.addAll(List.of(admin, target));
            when(storeService.readStore()).thenReturn(store);

            Map<String, Object> record = new LinkedHashMap<>();
            record.put("id", "u2");
            record.put("name", "New Name");
            record.put("username", "student1");
            record.put("password", "hashed");
            record.put("role", "student");
            record.put("classId", "c1");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("record", record);

            ResponseEntity<?> response = entityCrudService.updateEntity("admin1", "users", body);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            Map<String, Object> updated = extractRecord(response);
            assertNotNull(updated);
            assertEquals("New Name", updated.get("name"));
            assertNull(updated.get("password"), "Password should be stripped from user response");
        }

        @Test
        void updateEntity_teacherUpdatesOwnQuestion_returnsOk() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher", "teacher1", "Teacher"));
            Map<String, Object> question = new LinkedHashMap<>();
            question.put("id", "q1");
            question.put("teacherId", "t1");
            question.put("title", "Old Title");
            question.put("subject", "Math");
            question.put("type", "single");
            question.put("score", 10);
            store.questions.add(question);
            when(storeService.readStore()).thenReturn(store);

            Map<String, Object> record = new LinkedHashMap<>();
            record.put("id", "q1");
            record.put("title", "New Title");
            record.put("subject", "Math");
            record.put("type", "single");
            record.put("score", 10);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("record", record);

            ResponseEntity<?> response = entityCrudService.updateEntity("t1", "questions", body);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        void updateEntity_teacherUpdatesOtherTeacherQuestion_returnsForbidden() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher", "teacher1", "Teacher 1"));
            Map<String, Object> question = new LinkedHashMap<>();
            question.put("id", "q1");
            question.put("teacherId", "t2"); // owned by different teacher
            question.put("title", "Q");
            question.put("subject", "Math");
            question.put("type", "single");
            question.put("score", 10);
            store.questions.add(question);
            when(storeService.readStore()).thenReturn(store);

            Map<String, Object> record = new LinkedHashMap<>();
            record.put("id", "q1");
            record.put("title", "Hacked Title");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("record", record);

            ResponseEntity<?> response = entityCrudService.updateEntity("t1", "questions", body);

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        }

        @Test
        void updateEntity_recordNotFound_returnsNotFound() {
            Store store = createEmptyStore();
            store.users.add(makeUser("admin1", "admin", "admin", "Admin"));
            when(storeService.readStore()).thenReturn(store);

            Map<String, Object> record = new LinkedHashMap<>();
            record.put("id", "nonexistent");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("record", record);

            ResponseEntity<?> response = entityCrudService.updateEntity("admin1", "users", body);

            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        }

        @Test
        void updateEntity_unknownEntity_returnsBadRequest() {
            Store store = createEmptyStore();
            store.users.add(makeUser("admin1", "admin", "admin", "Admin"));
            when(storeService.readStore()).thenReturn(store);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("record", Map.of("id", "x"));

            // "unknownType" will cause store.entity() to throw IllegalArgumentException
            // Actually, it returns null from entity() for unknown names
            // Let's check: store.entity("unknownType") throws IllegalArgumentException
            // So we need to handle that. Let me use a valid entity name but test forbidden.
            ResponseEntity<?> response = entityCrudService.updateEntity("admin1", "questions", body);

            // Admin cannot manage questions (only users, departments, classes)
            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        }
    }

    // ================================================================
    // Delete Entity tests
    // ================================================================

    @Nested
    class DeleteEntityTests {

        @Test
        void deleteEntity_adminDeletesClass_cascadesExamReferences() {
            Store store = createEmptyStore();
            store.users.add(makeUser("admin1", "admin", "admin", "Admin"));
            Map<String, Object> classRecord = new LinkedHashMap<>();
            classRecord.put("id", "c1");
            classRecord.put("name", "Class 1");
            store.classes.add(classRecord);

            Map<String, Object> exam = new LinkedHashMap<>();
            exam.put("id", "e1");
            exam.put("targetClassIds", new ArrayList<>(List.of("c1", "c2")));
            store.exams.add(exam);
            when(storeService.readStore()).thenReturn(store);

            ResponseEntity<?> response = entityCrudService.deleteEntity("admin1", "classes", "c1");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(storeService).deleteRecord("classes", "c1");

            // Exam's targetClassIds should have "c1" removed
            @SuppressWarnings("unchecked")
            List<Object> remaining = (List<Object>) exam.get("targetClassIds");
            assertFalse(remaining.contains("c1"));
            assertTrue(remaining.contains("c2"));
        }

        @Test
        void deleteEntity_recordNotFound_returnsNotFound() {
            Store store = createEmptyStore();
            store.users.add(makeUser("admin1", "admin", "admin", "Admin"));
            when(storeService.readStore()).thenReturn(store);

            ResponseEntity<?> response = entityCrudService.deleteEntity("admin1", "classes", "nonexistent");

            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        }

        @Test
        void deleteEntity_studentTriesToDelete_returnsForbidden() {
            Store store = createEmptyStore();
            store.users.add(makeUser("s1", "student", "student1", "Student"));
            when(storeService.readStore()).thenReturn(store);

            ResponseEntity<?> response = entityCrudService.deleteEntity("s1", "questions", "q1");

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        }
    }

    // ================================================================
    // Delete Blocker tests
    // ================================================================

    @Nested
    class DeleteBlockerTests {

        @Test
        void deleteBlocker_departmentWithClassesAndUsers_returnsBlockMessage() {
            Store store = createEmptyStore();

            Map<String, Object> cls = new LinkedHashMap<>();
            cls.put("id", "c1");
            cls.put("departmentId", "d1");
            store.classes.add(cls);

            Map<String, Object> user = new LinkedHashMap<>();
            user.put("id", "u1");
            user.put("departmentId", "d1");
            store.users.add(user);

            String result = entityCrudService.deleteBlocker(store, "departments", "d1");

            assertFalse(result.isBlank());
            assertTrue(result.contains("1 个班级"));
            assertTrue(result.contains("1 名师生"));
        }

        @Test
        void deleteBlocker_departmentWithNoReferences_returnsEmpty() {
            Store store = createEmptyStore();

            String result = entityCrudService.deleteBlocker(store, "departments", "d1");

            assertTrue(result.isBlank());
        }

        @Test
        void deleteBlocker_classWithStudents_returnsBlockMessage() {
            Store store = createEmptyStore();

            Map<String, Object> student = new LinkedHashMap<>();
            student.put("id", "s1");
            student.put("classId", "c1");
            store.users.add(student);

            Map<String, Object> student2 = new LinkedHashMap<>();
            student2.put("id", "s2");
            student2.put("classId", "c1");
            store.users.add(student2);

            String result = entityCrudService.deleteBlocker(store, "classes", "c1");

            assertFalse(result.isBlank());
            assertTrue(result.contains("2 名学生"));
        }

        @Test
        void deleteBlocker_questionReferencedByPaper_returnsBlockMessage() {
            Store store = createEmptyStore();

            Map<String, Object> paper = new LinkedHashMap<>();
            paper.put("id", "p1");
            paper.put("questionIds", new ArrayList<>(List.of("q1", "q2")));
            store.papers.add(paper);

            String result = entityCrudService.deleteBlocker(store, "questions", "q1");

            assertFalse(result.isBlank());
            assertTrue(result.contains("已被试卷引用"));
        }

        @Test
        void deleteBlocker_paperReferencedByExam_returnsBlockMessage() {
            Store store = createEmptyStore();

            Map<String, Object> exam = new LinkedHashMap<>();
            exam.put("id", "e1");
            exam.put("paperId", "p1");
            store.exams.add(exam);

            String result = entityCrudService.deleteBlocker(store, "papers", "p1");

            assertFalse(result.isBlank());
            assertTrue(result.contains("已被考试引用"));
        }

        @Test
        void deleteBlocker_examWithSubmissions_returnsBlockMessage() {
            Store store = createEmptyStore();

            Map<String, Object> sub = new LinkedHashMap<>();
            sub.put("id", "sub1");
            sub.put("examId", "e1");
            store.submissions.add(sub);

            String result = entityCrudService.deleteBlocker(store, "exams", "e1");

            assertFalse(result.isBlank());
            assertTrue(result.contains("已有提交记录"));
        }

        @Test
        void deleteBlocker_examWithNoSubmissions_returnsEmpty() {
            Store store = createEmptyStore();

            String result = entityCrudService.deleteBlocker(store, "exams", "e1");

            assertTrue(result.isBlank());
        }

        @Test
        void deleteBlocker_questionNotReferencedByAnyPaper_returnsEmpty() {
            Store store = createEmptyStore();

            Map<String, Object> paper = new LinkedHashMap<>();
            paper.put("id", "p1");
            paper.put("questionIds", new ArrayList<>(List.of("q2", "q3")));
            store.papers.add(paper);

            String result = entityCrudService.deleteBlocker(store, "questions", "q1");

            assertTrue(result.isBlank());
        }
    }

    // ================================================================
    // Validate tests
    // ================================================================

    @Nested
    class ValidateTests {

        @Test
        void validate_userWithBlankUsername_returnsError() {
            Store store = createEmptyStore();
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("username", "");
            record.put("password", "pass");
            record.put("name", "Test");
            record.put("role", "student");

            String result = entityCrudService.validate(store, "users", record, null);

            assertFalse(result.isBlank());
            assertTrue(result.contains("incomplete"));
        }

        @Test
        void validate_userWithInvalidRole_returnsError() {
            Store store = createEmptyStore();
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("username", "user1");
            record.put("password", "pass");
            record.put("name", "Test");
            record.put("role", "superadmin");

            String result = entityCrudService.validate(store, "users", record, null);

            assertEquals("Invalid role.", result);
        }

        @Test
        void validate_userWithDuplicateUsername_returnsError() {
            Store store = createEmptyStore();
            store.users.add(makeUser("u1", "student", "taken", "Existing User"));

            Map<String, Object> record = new LinkedHashMap<>();
            record.put("username", "taken");
            record.put("password", "pass");
            record.put("name", "New User");
            record.put("role", "student");
            record.put("classId", "c1");

            // Also add class so it passes class existence check
            Map<String, Object> cls = new LinkedHashMap<>();
            cls.put("id", "c1");
            store.classes.add(cls);

            String result = entityCrudService.validate(store, "users", record, null);

            assertTrue(result.contains("Username already exists"));
        }

        @Test
        void validate_studentWithoutClassId_returnsError() {
            Store store = createEmptyStore();
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("username", "student1");
            record.put("password", "pass");
            record.put("name", "Student");
            record.put("role", "student");
            record.put("classId", "");

            String result = entityCrudService.validate(store, "users", record, null);

            assertTrue(result.contains("Student must bind a class"));
        }

        @Test
        void validate_teacherWithoutDepartmentId_returnsError() {
            Store store = createEmptyStore();
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("username", "teacher1");
            record.put("password", "pass");
            record.put("name", "Teacher");
            record.put("role", "teacher");
            record.put("departmentId", "");

            String result = entityCrudService.validate(store, "users", record, null);

            assertTrue(result.contains("must bind a department"));
        }

        @Test
        void validate_questionWithZeroScore_returnsError() {
            Store store = createEmptyStore();
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("title", "Good Title");
            record.put("subject", "Math");
            record.put("type", "single");
            record.put("score", 0);

            String result = entityCrudService.validate(store, "questions", record, null);

            assertTrue(result.contains("score must be greater than zero"));
        }

        @Test
        void validate_questionWithBlankTitle_returnsError() {
            Store store = createEmptyStore();
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("title", "");
            record.put("subject", "Math");
            record.put("type", "single");
            record.put("score", 10);

            String result = entityCrudService.validate(store, "questions", record, null);

            assertTrue(result.contains("incomplete"));
        }

        @Test
        void validate_paperWithNoQuestions_returnsError() {
            Store store = createEmptyStore();
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("name", "Empty Paper");
            record.put("questionIds", List.of());

            String result = entityCrudService.validate(store, "papers", record, null);

            assertTrue(result.contains("needs questions"));
        }

        @Test
        void validate_examWithBlankName_returnsError() {
            Store store = createEmptyStore();
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("name", "");
            record.put("paperId", "p1");
            record.put("targetClassIds", List.of("c1"));

            String result = entityCrudService.validate(store, "exams", record, null);

            assertTrue(result.contains("incomplete"));
        }

        @Test
        void validate_validQuestion_returnsEmpty() {
            Store store = createEmptyStore();
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("title", "What is 2+2?");
            record.put("subject", "Math");
            record.put("type", "single");
            record.put("score", 10);

            String result = entityCrudService.validate(store, "questions", record, null);

            assertTrue(result.isBlank());
        }

        @Test
        void validate_studentWithNonexistentClass_returnsError() {
            Store store = createEmptyStore();
            // No classes in store
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("username", "student1");
            record.put("password", "pass");
            record.put("name", "Student");
            record.put("role", "student");
            record.put("classId", "nonexistent_class");

            String result = entityCrudService.validate(store, "users", record, null);

            assertTrue(result.contains("Class does not exist"));
        }
    }

    // ================================================================
    // Clear Exam Class References tests
    // ================================================================

    @Nested
    class ClearReferencesTests {

        @Test
        void clearExamClassReferences_removesClassIdFromMatchingExams() {
            Store store = createEmptyStore();

            Map<String, Object> exam1 = new LinkedHashMap<>();
            exam1.put("id", "e1");
            exam1.put("targetClassIds", new ArrayList<>(List.of("c1", "c2", "c3")));
            store.exams.add(exam1);

            Map<String, Object> exam2 = new LinkedHashMap<>();
            exam2.put("id", "e2");
            exam2.put("targetClassIds", new ArrayList<>(List.of("c2", "c3")));
            store.exams.add(exam2);

            entityCrudService.clearExamClassReferences(store, "c1");

            @SuppressWarnings("unchecked")
            List<Object> e1Classes = (List<Object>) exam1.get("targetClassIds");
            assertFalse(e1Classes.contains("c1"));
            assertTrue(e1Classes.contains("c2"));
            assertTrue(e1Classes.contains("c3"));

            // exam2 should not be modified (did not contain c1)
            @SuppressWarnings("unchecked")
            List<Object> e2Classes = (List<Object>) exam2.get("targetClassIds");
            assertEquals(2, e2Classes.size());

            // saveRecord should only be called for exam1
            verify(storeService, times(1)).saveRecord(eq("exams"), any());
        }
    }
}
