package com.onlineexam.service;

import com.onlineexam.StoreService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChoiceSubmissionIntegrityTest {
  private final SubmissionService service = new SubmissionService(mock(StoreService.class),
      mock(ExamService.class), mock(SystemLogService.class), mock(WrongBookService.class));

  @Test void singleChoiceCannotReceiveFullMarksWithSeveralAnswers() {
    var question = Map.<String, Object>of("type", "single", "options", List.of("Red", "Blue"), "answer", List.of("Red"));
    assertEquals(false, service.compare(question, List.of("Red", "Blue"), false).correct());
  }

  @Test void judgmentCannotReceiveFullMarksForBothOptions() {
    var question = Map.<String, Object>of("type", "judge", "options", List.of("正确", "错误"), "answer", List.of("正确"));
    assertEquals(false, service.compare(question, List.of("正确", "错误"), false).correct());
  }

  @Test void singleChoiceStillAcceptsOneOriginalOptionLabel() {
    var question = Map.<String, Object>of("type", "single", "options", List.of("Red", "Blue"), "answer", List.of("Red"));
    assertEquals(true, service.compare(question, List.of("A"), false).correct());
  }
}
