package com.onlineexam.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlineexam.StoreService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiIntentAndPromptTest {

  private AiService aiService;

  @BeforeEach
  void setUp() {
    StoreService storage = mock(StoreService.class);
    SystemLogService logs = mock(SystemLogService.class);
    RestTemplate rest = mock(RestTemplate.class);
    ObjectMapper mapper = new ObjectMapper();
    AiCircuitBreaker circuit = mock(AiCircuitBreaker.class);
    when(circuit.allowRequest(anyString())).thenReturn(true);

    aiService = new AiService(storage, logs, rest, mapper, Runnable::run, circuit);
  }

  @Test
  void testSubjectExtractionDoesNotTreatTypeOrNoiseAsSubject() {
    // Question type phrases must not become subjects
    AiService.UserIntent intent1 = aiService.analyzeUserIntent("帮我出两道单选题考查微积分基本定理");
    assertEquals("微积分", intent1.subject);
    assertNotEquals("单选", intent1.subject);
    assertNotEquals("两道", intent1.subject);

    AiService.UserIntent intent2 = aiService.analyzeUserIntent("出3道多选题考查Java多线程死锁");
    assertEquals("Java", intent2.subject);
    assertNotEquals("多选", intent2.subject);

    AiService.UserIntent intent3 = aiService.analyzeUserIntent("请出两道变式题");
    assertNotEquals("变式", intent3.subject);

    AiService.UserIntent intent4 = aiService.analyzeUserIntent("出几道真题");
    assertNotEquals("真", intent4.subject);
    assertNotEquals("真题", intent4.subject);
  }

  @Test
  void testPredefinedSubjectMatching() {
    AiService.UserIntent intentMath = aiService.analyzeUserIntent("给我出5道关于高等数学的题");
    assertEquals("高等数学", intentMath.subject);

    AiService.UserIntent intentNet = aiService.analyzeUserIntent("出3道计算机网络选择题");
    assertEquals("计算机网络", intentNet.subject);

    AiService.UserIntent intentPython = aiService.analyzeUserIntent("帮我出Python编程题");
    assertEquals("Python", intentPython.subject);
  }

  @Test
  void testCountExtractionSupportsChineseAndArabicNumbers() {
    AiService.UserIntent intentCn1 = aiService.analyzeUserIntent("出两道高等数学单选题");
    assertEquals(2, intentCn1.count);

    AiService.UserIntent intentCn2 = aiService.analyzeUserIntent("帮我出三题Java多线程");
    assertEquals(3, intentCn2.count);

    AiService.UserIntent intentCn3 = aiService.analyzeUserIntent("来一道判断题");
    assertEquals(1, intentCn3.count);

    AiService.UserIntent intentAr = aiService.analyzeUserIntent("出5个单选");
    assertEquals(5, intentAr.count);
  }

  @Test
  void testTypeExtractionSupportsGeneralSynonyms() {
    AiService.UserIntent intentSingle = aiService.analyzeUserIntent("出3道关于微积分的选择题");
    assertEquals("single", intentSingle.type);

    AiService.UserIntent intentShort = aiService.analyzeUserIntent("出2道关于TCP三次握手的问答题");
    assertEquals("short", intentShort.type);

    AiService.UserIntent intentCoding = aiService.analyzeUserIntent("出一道快速排序的算法题");
    assertEquals("coding", intentCoding.type);
  }

  @Test
  void testChoiceAnswersStripsRepeatedPrefixes() {
    assertEquals("微积分", ChoiceAnswers.choiceContent("A. 微积分"));
    assertEquals("微积分", ChoiceAnswers.choiceContent("A. A. 微积分"));
    assertEquals("微积分", ChoiceAnswers.choiceContent("(A) A. 微积分"));
    assertEquals("微积分", ChoiceAnswers.choiceContent("A、 A. 微积分"));
    assertEquals("微积分", ChoiceAnswers.choiceContent("【A】微积分"));
    assertEquals("", ChoiceAnswers.choiceContent("A."));
  }

  @Test
  void testChoiceAnswersValidatesJudgeWithChineseAliases() {
    List<String> options = List.of("A. 正确", "B. 错误");
    List<String> answerTrue = List.of("正确");
    List<String> validated = ChoiceAnswers.validate(options, answerTrue, "judge");
    assertEquals(List.of("A. 正确"), validated);

    List<String> answerFalse = List.of("错误");
    List<String> validatedFalse = ChoiceAnswers.validate(options, answerFalse, "judge");
    assertEquals(List.of("B. 错误"), validatedFalse);
  }
}
