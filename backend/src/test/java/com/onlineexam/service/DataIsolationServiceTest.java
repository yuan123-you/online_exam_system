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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataIsolationServiceTest {

    @Mock
    private StoreService storeService;

    @InjectMocks
    private DataIsolationService dataIsolationService;

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

    private Map<String, Object> makeUser(String id, String role, String classId) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", id);
        user.put("role", role);
        user.put("classId", classId);
        return user;
    }

    private Map<String, Object> makeQuestion(String id, String teacherId) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("id", id);
        q.put("teacherId", teacherId);
        return q;
    }

    private Map<String, Object> makePaper(String id, String teacherId) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", id);
        p.put("teacherId", teacherId);
        return p;
    }

    private Map<String, Object> makeExam(String id, String teacherId, boolean published, List<Object> targetClassIds) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("id", id);
        e.put("teacherId", teacherId);
        e.put("published", published);
        e.put("targetClassIds", targetClassIds);
        return e;
    }

    private Map<String, Object> makeSubmission(String id, String studentId, String examId) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("id", id);
        s.put("studentId", studentId);
        s.put("examId", examId);
        return s;
    }

    private Map<String, Object> makeWrongBookEntry(String id, String studentId) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("id", id);
        e.put("studentId", studentId);
        return e;
    }

    // ================================================================
    // canAccessQuestion tests
    // ================================================================

    @Nested
    class CanAccessQuestionTests {

        @Test
        void admin_canAccessAnyQuestion() {
            Store store = createEmptyStore();
            store.users.add(makeUser("admin1", "admin", null));
            store.questions.add(makeQuestion("q1", "teacher1"));
            when(storeService.readStore()).thenReturn(store);

            assertTrue(dataIsolationService.canAccessQuestion("admin1", "q1"));
        }

        @Test
        void teacher_canAccessOwnQuestion() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher", null));
            store.questions.add(makeQuestion("q1", "t1"));
            when(storeService.readStore()).thenReturn(store);

            assertTrue(dataIsolationService.canAccessQuestion("t1", "q1"));
        }

        @Test
        void teacher_cannotAccessOtherTeacherQuestion() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher", null));
            store.questions.add(makeQuestion("q1", "t2"));
            when(storeService.readStore()).thenReturn(store);

            assertFalse(dataIsolationService.canAccessQuestion("t1", "q1"));
        }

        @Test
        void student_cannotAccessAnyQuestion() {
            Store store = createEmptyStore();
            store.users.add(makeUser("s1", "student", "c1"));
            store.questions.add(makeQuestion("q1", "t1"));
            when(storeService.readStore()).thenReturn(store);

            assertFalse(dataIsolationService.canAccessQuestion("s1", "q1"));
        }

        @Test
        void unknownUser_returnsFalse() {
            Store store = createEmptyStore();
            store.questions.add(makeQuestion("q1", "t1"));
            when(storeService.readStore()).thenReturn(store);

            assertFalse(dataIsolationService.canAccessQuestion("unknown", "q1"));
        }
    }

    // ================================================================
    // canAccessPaper tests
    // ================================================================

    @Nested
    class CanAccessPaperTests {

        @Test
        void admin_canAccessAnyPaper() {
            Store store = createEmptyStore();
            store.users.add(makeUser("admin1", "admin", null));
            store.papers.add(makePaper("p1", "teacher1"));
            when(storeService.readStore()).thenReturn(store);

            assertTrue(dataIsolationService.canAccessPaper("admin1", "p1"));
        }

        @Test
        void teacher_canAccessOwnPaper() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher", null));
            store.papers.add(makePaper("p1", "t1"));
            when(storeService.readStore()).thenReturn(store);

            assertTrue(dataIsolationService.canAccessPaper("t1", "p1"));
        }

        @Test
        void teacher_cannotAccessOtherTeacherPaper() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher", null));
            store.papers.add(makePaper("p1", "t2"));
            when(storeService.readStore()).thenReturn(store);

            assertFalse(dataIsolationService.canAccessPaper("t1", "p1"));
        }

        @Test
        void student_cannotAccessPaper() {
            Store store = createEmptyStore();
            store.users.add(makeUser("s1", "student", "c1"));
            store.papers.add(makePaper("p1", "t1"));
            when(storeService.readStore()).thenReturn(store);

            assertFalse(dataIsolationService.canAccessPaper("s1", "p1"));
        }
    }

    // ================================================================
    // canAccessExam tests
    // ================================================================

    @Nested
    class CanAccessExamTests {

        @Test
        void admin_canAccessAnyExam() {
            Store store = createEmptyStore();
            store.users.add(makeUser("admin1", "admin", null));
            store.exams.add(makeExam("e1", "t1", false, List.of()));
            when(storeService.readStore()).thenReturn(store);

            assertTrue(dataIsolationService.canAccessExam("admin1", "e1"));
        }

        @Test
        void teacher_canAccessOwnExam() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher", null));
            store.exams.add(makeExam("e1", "t1", false, List.of()));
            when(storeService.readStore()).thenReturn(store);

            assertTrue(dataIsolationService.canAccessExam("t1", "e1"));
        }

        @Test
        void teacher_cannotAccessOtherTeacherExam() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher", null));
            store.exams.add(makeExam("e1", "t2", false, List.of()));
            when(storeService.readStore()).thenReturn(store);

            assertFalse(dataIsolationService.canAccessExam("t1", "e1"));
        }

        @Test
        void student_canAccessPublishedExamWithMatchingClass() {
            Store store = createEmptyStore();
            store.users.add(makeUser("s1", "student", "c1"));
            store.exams.add(makeExam("e1", "t1", true, List.of("c1", "c2")));
            when(storeService.readStore()).thenReturn(store);

            assertTrue(dataIsolationService.canAccessExam("s1", "e1"));
        }

        @Test
        void student_cannotAccessUnpublishedExam() {
            Store store = createEmptyStore();
            store.users.add(makeUser("s1", "student", "c1"));
            store.exams.add(makeExam("e1", "t1", false, List.of("c1")));
            when(storeService.readStore()).thenReturn(store);

            assertFalse(dataIsolationService.canAccessExam("s1", "e1"));
        }

        @Test
        void student_cannotAccessExamWithDifferentClass() {
            Store store = createEmptyStore();
            store.users.add(makeUser("s1", "student", "c3"));
            store.exams.add(makeExam("e1", "t1", true, List.of("c1", "c2")));
            when(storeService.readStore()).thenReturn(store);

            assertFalse(dataIsolationService.canAccessExam("s1", "e1"));
        }
    }

    // ================================================================
    // canAccessSubmission tests
    // ================================================================

    @Nested
    class CanAccessSubmissionTests {

        @Test
        void admin_canAccessAnySubmission() {
            Store store = createEmptyStore();
            store.users.add(makeUser("admin1", "admin", null));
            store.submissions.add(makeSubmission("sub1", "s1", "e1"));
            when(storeService.readStore()).thenReturn(store);

            assertTrue(dataIsolationService.canAccessSubmission("admin1", "sub1"));
        }

        @Test
        void teacher_canAccessSubmissionOfOwnExam() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher", null));
            store.exams.add(makeExam("e1", "t1", true, List.of()));
            store.submissions.add(makeSubmission("sub1", "s1", "e1"));
            when(storeService.readStore()).thenReturn(store);

            assertTrue(dataIsolationService.canAccessSubmission("t1", "sub1"));
        }

        @Test
        void teacher_cannotAccessSubmissionOfOtherTeacherExam() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher", null));
            store.exams.add(makeExam("e1", "t2", true, List.of()));
            store.submissions.add(makeSubmission("sub1", "s1", "e1"));
            when(storeService.readStore()).thenReturn(store);

            assertFalse(dataIsolationService.canAccessSubmission("t1", "sub1"));
        }

        @Test
        void student_canAccessOwnSubmission() {
            Store store = createEmptyStore();
            store.users.add(makeUser("s1", "student", "c1"));
            store.submissions.add(makeSubmission("sub1", "s1", "e1"));
            when(storeService.readStore()).thenReturn(store);

            assertTrue(dataIsolationService.canAccessSubmission("s1", "sub1"));
        }

        @Test
        void student_cannotAccessOtherStudentSubmission() {
            Store store = createEmptyStore();
            store.users.add(makeUser("s1", "student", "c1"));
            store.submissions.add(makeSubmission("sub1", "s2", "e1"));
            when(storeService.readStore()).thenReturn(store);

            assertFalse(dataIsolationService.canAccessSubmission("s1", "sub1"));
        }
    }

    // ================================================================
    // ownsQuestion / ownsPaper / ownsExam tests
    // ================================================================

    @Nested
    class OwnsEntityTests {

        @Test
        void ownsQuestion_returnsTrueWhenTeacherIdMatches() {
            Store store = createEmptyStore();
            store.questions.add(makeQuestion("q1", "t1"));
            when(storeService.readStore()).thenReturn(store);

            assertTrue(dataIsolationService.ownsQuestion("t1", "q1"));
        }

        @Test
        void ownsQuestion_returnsFalseWhenTeacherIdDoesNotMatch() {
            Store store = createEmptyStore();
            store.questions.add(makeQuestion("q1", "t2"));
            when(storeService.readStore()).thenReturn(store);

            assertFalse(dataIsolationService.ownsQuestion("t1", "q1"));
        }

        @Test
        void ownsQuestion_returnsFalseWhenEntityNotFound() {
            Store store = createEmptyStore();
            when(storeService.readStore()).thenReturn(store);

            assertFalse(dataIsolationService.ownsQuestion("t1", "nonexistent"));
        }

        @Test
        void ownsPaper_returnsTrueWhenTeacherIdMatches() {
            Store store = createEmptyStore();
            store.papers.add(makePaper("p1", "t1"));
            when(storeService.readStore()).thenReturn(store);

            assertTrue(dataIsolationService.ownsPaper("t1", "p1"));
        }

        @Test
        void ownsPaper_returnsFalseWhenTeacherIdDoesNotMatch() {
            Store store = createEmptyStore();
            store.papers.add(makePaper("p1", "t2"));
            when(storeService.readStore()).thenReturn(store);

            assertFalse(dataIsolationService.ownsPaper("t1", "p1"));
        }

        @Test
        void ownsPaper_returnsFalseWhenEntityNotFound() {
            Store store = createEmptyStore();
            when(storeService.readStore()).thenReturn(store);

            assertFalse(dataIsolationService.ownsPaper("t1", "nonexistent"));
        }

        @Test
        void ownsExam_returnsTrueWhenTeacherIdMatches() {
            Store store = createEmptyStore();
            store.exams.add(makeExam("e1", "t1", true, List.of()));
            when(storeService.readStore()).thenReturn(store);

            assertTrue(dataIsolationService.ownsExam("t1", "e1"));
        }

        @Test
        void ownsExam_returnsFalseWhenTeacherIdDoesNotMatch() {
            Store store = createEmptyStore();
            store.exams.add(makeExam("e1", "t2", true, List.of()));
            when(storeService.readStore()).thenReturn(store);

            assertFalse(dataIsolationService.ownsExam("t1", "e1"));
        }

        @Test
        void ownsExam_returnsFalseWhenEntityNotFound() {
            Store store = createEmptyStore();
            when(storeService.readStore()).thenReturn(store);

            assertFalse(dataIsolationService.ownsExam("t1", "nonexistent"));
        }
    }

    // ================================================================
    // canManageExam tests
    // ================================================================

    @Nested
    class CanManageExamTests {

        @Test
        void admin_canManageAnyExam() {
            Store store = createEmptyStore();
            store.users.add(makeUser("admin1", "admin", null));
            store.exams.add(makeExam("e1", "t1", false, List.of()));
            when(storeService.readStore()).thenReturn(store);

            assertTrue(dataIsolationService.canManageExam("admin1", "e1"));
        }

        @Test
        void teacher_canManageOwnExam() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher", null));
            store.exams.add(makeExam("e1", "t1", false, List.of()));
            when(storeService.readStore()).thenReturn(store);

            assertTrue(dataIsolationService.canManageExam("t1", "e1"));
        }

        @Test
        void teacher_cannotManageOtherTeacherExam() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher", null));
            store.exams.add(makeExam("e1", "t2", false, List.of()));
            when(storeService.readStore()).thenReturn(store);

            assertFalse(dataIsolationService.canManageExam("t1", "e1"));
        }
    }

    // ================================================================
    // canAccessWrongBookEntry tests
    // ================================================================

    @Nested
    class CanAccessWrongBookEntryTests {

        @Test
        void admin_canAccessAnyEntry() {
            Store store = createEmptyStore();
            store.users.add(makeUser("admin1", "admin", null));
            store.wrongBookEntries.add(makeWrongBookEntry("w1", "s1"));
            when(storeService.readStore()).thenReturn(store);

            assertTrue(dataIsolationService.canAccessWrongBookEntry("admin1", "w1"));
        }

        @Test
        void student_canAccessOwnEntry() {
            Store store = createEmptyStore();
            store.users.add(makeUser("s1", "student", "c1"));
            store.wrongBookEntries.add(makeWrongBookEntry("w1", "s1"));
            when(storeService.readStore()).thenReturn(store);

            assertTrue(dataIsolationService.canAccessWrongBookEntry("s1", "w1"));
        }

        @Test
        void student_cannotAccessOtherStudentEntry() {
            Store store = createEmptyStore();
            store.users.add(makeUser("s1", "student", "c1"));
            store.wrongBookEntries.add(makeWrongBookEntry("w1", "s2"));
            when(storeService.readStore()).thenReturn(store);

            assertFalse(dataIsolationService.canAccessWrongBookEntry("s1", "w1"));
        }

        @Test
        void teacher_cannotAccessWrongBookEntry() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher", null));
            store.wrongBookEntries.add(makeWrongBookEntry("w1", "s1"));
            when(storeService.readStore()).thenReturn(store);

            assertFalse(dataIsolationService.canAccessWrongBookEntry("t1", "w1"));
        }
    }

    // ================================================================
    // Role check tests
    // ================================================================

    @Nested
    class RoleCheckTests {

        @Test
        void isAdmin_returnsTrueForAdminUser() {
            Store store = createEmptyStore();
            store.users.add(makeUser("admin1", "admin", null));
            when(storeService.readStore()).thenReturn(store);

            assertTrue(dataIsolationService.isAdmin("admin1"));
        }

        @Test
        void isAdmin_returnsFalseForNonAdminUser() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher", null));
            when(storeService.readStore()).thenReturn(store);

            assertFalse(dataIsolationService.isAdmin("t1"));
        }

        @Test
        void isTeacher_returnsTrueForTeacherUser() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher", null));
            when(storeService.readStore()).thenReturn(store);

            assertTrue(dataIsolationService.isTeacher("t1"));
        }

        @Test
        void isTeacher_returnsFalseForNonTeacherUser() {
            Store store = createEmptyStore();
            store.users.add(makeUser("s1", "student", "c1"));
            when(storeService.readStore()).thenReturn(store);

            assertFalse(dataIsolationService.isTeacher("s1"));
        }

        @Test
        void isStudent_returnsTrueForStudentUser() {
            Store store = createEmptyStore();
            store.users.add(makeUser("s1", "student", "c1"));
            when(storeService.readStore()).thenReturn(store);

            assertTrue(dataIsolationService.isStudent("s1"));
        }

        @Test
        void isStudent_returnsFalseForNonStudentUser() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher", null));
            when(storeService.readStore()).thenReturn(store);

            assertFalse(dataIsolationService.isStudent("t1"));
        }

        @Test
        void roleChecks_returnFalseForUnknownUser() {
            Store store = createEmptyStore();
            when(storeService.readStore()).thenReturn(store);

            assertFalse(dataIsolationService.isAdmin("unknown"));
            assertFalse(dataIsolationService.isTeacher("unknown"));
            assertFalse(dataIsolationService.isStudent("unknown"));
        }
    }
}
