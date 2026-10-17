package com.onlineexam.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class AiQuestionLatexJsonTest {
  private final ObjectMapper json = new ObjectMapper();
  private String question(String title, String explanation) throws Exception {
    return json.writeValueAsString(List.of(Map.of("title", title, "explanation", explanation,
        "type", "single", "options", List.of("A. 1", "B. 2"), "answer", List.of("A"), "score", 5)));
  }
  private Map<String, Object> parse(String response) {
    return AiQuestionPolicy.parsePreview(response, json, "高等数学", "极限", "medium", "single").get(0);
  }
  @ParameterizedTest @ValueSource(strings = {
      "$\\lim_{x \\to 0} \\frac{\\sin x}{x}$",
      "$\\frac{1}{2}+\\beta+\\theta$",
      "$$\\int_0^1 x\\,dx$$"
  })
  void rawLatexCommandsRemainIntactWhenProviderOmitsJsonEscaping(String formula) throws Exception {
    String response = question(formula, formula).replace("\\\\", "\\");
    var result = assertDoesNotThrow(() -> parse(response));
    assertEquals(formula, result.get("title"));
    assertEquals(formula, result.get("explanation"));
  }
  @Test void correctlyEscapedLatexIsNotEscapedTwice() throws Exception {
    String formula = "$\\lim_{x \\to 0} \\frac{\\sin x}{x}$";
    assertEquals(formula, parse(question(formula, "正常解析")).get("title"));
  }
  @Test void ordinaryJsonEscapesOutsideMathRetainTheirMeaning() throws Exception {
    String explanation = "第一行\n第二行\t\"quoted\" C:\\temp";
    assertEquals(explanation, parse(question("普通题目", explanation)).get("explanation"));
  }
  @Test void legitimateShortJsonEscapeInsideMathIsPreserved() throws Exception {
    assertEquals("$x\ny$", parse(question("$x\ny$", "说明")).get("title"));
  }
  @Test void unknownEscapesOutsideMathStillFailRatherThanBeingRewritten() throws Exception {
    String response = question("ordinary", "说明").replace("ordinary", "bad \\q escape");
    assertThrows(IllegalArgumentException.class, () -> parse(response));
  }
  @Test void malformedArraysAndTrailingContentStillFail() throws Exception {
    String response = question("$\\lim x$", "说明").replace("\\\\", "\\");
    assertThrows(IllegalArgumentException.class, () -> parse(response.substring(0, response.length() - 1)));
    assertThrows(IllegalArgumentException.class, () -> parse(response + " unexpected"));
  }
  @ParameterizedTest @ValueSource(strings = {
      "$\\biggl(x\\biggr)$", "$x\\triangleq y$", "$\\textnormal{value}$"
  })
  void collidingCommandFamiliesRemainLatexInsteadOfJsonControlCharacters(String formula) throws Exception {
    assertEquals(formula, parse(question(formula, "说明").replace("\\\\", "\\")).get("title"));
  }
  @Test void escapedCurrencyDoesNotInvertTheFollowingMathDelimiters() throws Exception {
    String title = "price \\$5; compute $\\frac{1}{2}$";
    String response = question(title, "说明").replace("\\\\frac", "\\frac");
    assertEquals(title, parse(response).get("title"));
  }

}
