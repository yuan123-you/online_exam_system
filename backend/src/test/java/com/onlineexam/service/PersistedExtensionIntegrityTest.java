package com.onlineexam.service;

import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import com.onlineexam.controller.ExamController;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PersistedExtensionIntegrityTest {
  @Test void reloadedSessionKeepsPersistedExtendedDeadlineWithoutTransientFlag() {
    Store store=new Store();
    Map<String,Object> exam=Map.of("id","e1","paperId","p1","published",true,"startTime",Instant.now().minusSeconds(600).toString(),
        "endTime",Instant.now().plusSeconds(3600).toString());
    var user=Map.<String,Object>of("id","u1","name","Student");
    String deadline=Instant.now().plusSeconds(2400).toString();
    var persisted=new LinkedHashMap<String,Object>(Map.of("id","s1","examId","e1","studentId","u1","status","进行中",
        "startedAt",Instant.now().minusSeconds(300).toString(),"deadlineAt",deadline,"manualExtendedMinutes",30,"revision",4L));
    store.submissions.add(persisted);
    store.examSnapshots.put("e1",Map.of("schemaVersion",1,"paper",Map.of("id","p1","durationMinutes",10,"questionIds",List.of("q1")),
        "questions",List.of(Map.of("id","q1","options",List.of("A","B")))));
    var service=new ExamService(mock(StoreService.class),mock(SystemLogService.class));
    var session=service.ensureStudentSession(store,exam,user);
    assertEquals(deadline,session.get("deadlineAt")); assertEquals(4L,session.get("revision"));
    assertFalse(persisted.containsKey("manualExtended"));
  }
  @Test void rejectedExtensionCannotMutateSharedReadView() {
    var storage=mock(StoreService.class); var exams=mock(ExamService.class); var logs=mock(SystemLogService.class);
    var store=new Store(); store.users.add(Map.of("id","t1","role","teacher"));
    store.exams.add(Map.of("id","e1","teacherId","t1"));
    var submission=new LinkedHashMap<String,Object>(Map.of("id","s1","status","进行中","deadlineAt",Instant.now().plusSeconds(600).toString(),
        "manualExtendedMinutes",0,"revision",0L)); var before=new LinkedHashMap<>(submission);
    when(storage.readStore()).thenReturn(store); when(exams.studentSubmission(store,"e1","u1")).thenReturn(submission);
    doThrow(new ResponseStatusException(HttpStatus.CONFLICT,"changed")).when(storage).saveRecord(eq("submissions"),anyMap());
    var controller=new ExamController(storage,exams,mock(SubmissionService.class),logs,mock(ExcelExportService.class));
    assertThrows(ResponseStatusException.class,()->controller.extendStudent("t1","e1",Map.of("studentId","u1","extraMinutes",5)));
    assertEquals(before,submission); verifyNoInteractions(logs);
  }
}
