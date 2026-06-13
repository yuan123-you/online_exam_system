package com.onlineexam.service;

import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 考试服务 - 处理考试详情、会话管理和监控
 */
@Service
public class ExamService {

  private static final String RUNNING = "\u8fdb\u884c\u4e2d";
  private static final String UPCOMING = "\u672a\u5f00\u59cb";
  private static final String ENDED = "\u5df2\u7ed3\u675f";
  private static final String PENDING = "\u5f85\u9605\u5377";
  private static final String COMPLETED = "\u5df2\u5b8c\u6210";

  private final StoreService storeService;
  private final SystemLogService systemLogService;

  public ExamService(StoreService storeService, SystemLogService systemLogService) {
    this.storeService = storeService;
    this.systemLogService = systemLogService;
  }

  /**
   * 获取考试状态
   */
  public String examStatus(Map<String, Object> exam) {
    Instant now = Instant.now();
    Instant start = parseInstant(str(exam, "startTime")).orElse(now);
    Instant end = parseInstant(str(exam, "endTime")).orElse(now);
    if (now.isBefore(start)) return UPCOMING;
    if (now.isAfter(end)) return ENDED;
    return RUNNING;
  }

  /**
   * 装饰考试信息（添加状态和试卷信息）
   */
  public Map<String, Object> decorateExam(Store store, Map<String, Object> exam) {
    Map<String, Object> paper = find(store.papers, str(exam, "paperId"));
    Map<String, Object> next = new LinkedHashMap<>(exam);
    next.put("statusText", examStatus(exam));
    next.put("durationMinutes", paper == null ? 0 : asInt(paper.get("durationMinutes")));
    next.put("totalScore", paper == null ? 0 : asInt(paper.get("totalScore")));
    next.put("passScore", paper == null ? 0 : asInt(paper.get("passScore")));
    next.put("paperName", paper == null ? "-" : str(paper, "name"));
    return next;
  }

  /**
   * 检查学生是否可以访问考试
   */
  public boolean canStudentAccess(Map<String, Object> user, Map<String, Object> exam) {
    return isRole(user, "student") && Boolean.TRUE.equals(exam.get("published")) && asList(exam.get("targetClassIds")).contains(str(user, "classId"));
  }

  /**
   * 确保学生考试会话
   */
  public Map<String, Object> ensureStudentSession(Store store, Map<String, Object> exam, Map<String, Object> user) {
    String status = examStatus(exam);
    if (UPCOMING.equals(status)) return mapOf("error", "Exam has not started.");
    if (ENDED.equals(status)) return mapOf("error", ENDED);
    Map<String, Object> paper = find(store.papers, str(exam, "paperId"));
    if (paper == null) return mapOf("error", "Paper not found.");
    Map<String, Object> existing = studentSubmission(store, str(exam, "id"), str(user, "id"));
    if (existing != null && (PENDING.equals(str(existing, "status")) || COMPLETED.equals(str(existing, "status")))) {
      return mapOf("error", "Submission already finished.");
    }
    Map<String, Object> session = existing == null ? new LinkedHashMap<>() : new LinkedHashMap<>(existing);
    session.putIfAbsent("id", createId("submission"));
    session.put("examId", str(exam, "id"));
    session.put("studentId", str(user, "id"));
    session.put("studentName", str(user, "name"));
    session.putIfAbsent("answers", List.of());
    session.putIfAbsent("switchCount", 0);
    if (!session.containsKey("questionOrder")) {
      List<String> questionIds = new ArrayList<>(asList(paper.get("questionIds")).stream().map(String::valueOf).toList());
      java.util.Collections.shuffle(questionIds);
      session.put("questionOrder", questionIds);
    }
    if (!session.containsKey("optionOrder")) {
      Map<String, Object> optionOrder = new LinkedHashMap<>();
      for (Object qIdObj : asList(paper.get("questionIds"))) {
        Map<String, Object> q = find(store.questions, String.valueOf(qIdObj));
        if (q != null) {
          List<Object> options = asList(q.get("options"));
          if (options.size() > 1) {
            List<Integer> indices = new ArrayList<>();
            for (int i = 0; i < options.size(); i++) indices.add(i);
            java.util.Collections.shuffle(indices);
            optionOrder.put(String.valueOf(qIdObj), indices);
          }
        }
      }
      session.put("optionOrder", optionOrder);
    }
    session.put("status", RUNNING);
    session.putIfAbsent("startedAt", now());
    session.put("deadlineAt", computeDeadline(exam, paper, str(session, "startedAt")));
    session.put("updatedAt", now());
    return session;
  }

  /**
   * 构建考试快照
   */
  public Map<String, Object> buildExamSnapshot(Store store, Map<String, Object> exam, boolean hideAnswers, List<String> questionOrder, Map<String, Object> optionOrder) {
    Map<String, Object> paper = find(store.papers, str(exam, "paperId"));
    List<Object> questionIds = questionOrder != null
      ? new ArrayList<>(questionOrder)
      : asList(paper == null ? null : paper.get("questionIds"));
    List<Map<String, Object>> questions = new ArrayList<>();
    int order = 1;
    for (Object id : questionIds) {
      Map<String, Object> q = find(store.questions, String.valueOf(id));
      if (q != null) {
        Map<String, Object> next = hideAnswers ? sanitizeQuestion(q) : new LinkedHashMap<>(q);
        if (optionOrder != null && !optionOrder.isEmpty()) {
          List<Object> options = asList(q.get("options"));
          if (!options.isEmpty()) {
            Object rawIndices = optionOrder.get(String.valueOf(id));
            if (rawIndices instanceof List<?> rawList) {
              List<Integer> indices = new ArrayList<>();
              for (Object o : rawList) {
                if (o instanceof Number n) indices.add(n.intValue());
              }
              if (indices.size() == options.size()) {
                List<Object> shuffled = new ArrayList<>();
                for (int idx : indices) shuffled.add(options.get(idx));
                next.put("options", shuffled);
              }
            }
          }
        }
        next.put("order", order++);
        questions.add(next);
      }
    }
    Map<String, Object> snapshot = new LinkedHashMap<>(decorateExam(store, exam));
    snapshot.put("paper", paper == null ? Map.of() : paper);
    snapshot.put("questions", questions);
    return snapshot;
  }

  /**
   * 计算截止时间
   */
  public String computeDeadline(Map<String, Object> exam, Map<String, Object> paper, String startedAt) {
    Instant end = parseInstant(str(exam, "endTime")).orElse(Instant.now());
    Instant started = parseInstant(startedAt).orElse(Instant.now());
    Instant deadline = started.plusSeconds(asInt(paper.get("durationMinutes")) * 60L);
    return deadline.isBefore(end) ? deadline.toString() : end.toString();
  }

  /**
   * 计算剩余毫秒数
   */
  public long remainingMs(Map<String, Object> submission) {
    return parseInstant(str(submission, "deadlineAt")).map(i -> Math.max(0L, i.toEpochMilli() - System.currentTimeMillis())).orElse(0L);
  }

  /**
   * 查找学生的提交记录
   */
  public Map<String, Object> studentSubmission(Store store, String examId, String studentId) {
    return store.submissions.stream().filter(s -> Objects.equals(str(s, "examId"), examId) && Objects.equals(str(s, "studentId"), studentId)).findFirst().orElse(null);
  }

  private Map<String, Object> sanitizeQuestion(Map<String, Object> question) {
    Map<String, Object> safe = new LinkedHashMap<>(question);
    safe.remove("answer");
    return safe;
  }

  private Optional<Instant> parseInstant(String value) {
    if (value == null || value.isBlank()) return Optional.empty();
    try { return Optional.of(Instant.parse(value)); } catch (Exception e) { return Optional.empty(); }
  }

  private Map<String, Object> find(List<Map<String, Object>> rows, String id) {
    if (id == null) return null;
    return rows.stream().filter(row -> Objects.equals(str(row, "id"), id)).findFirst().orElse(null);
  }

  private boolean isRole(Map<String, Object> user, String role) {
    return user != null && Objects.equals(str(user, "role"), role);
  }

  private int asInt(Object value) {
    if (value instanceof Number n) return n.intValue();
    if (value == null || String.valueOf(value).isBlank()) return 0;
    try { return Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException e) { return 0; }
  }

  private List<Object> asList(Object raw) {
    return raw instanceof List<?> list ? new ArrayList<>(list) : new ArrayList<>();
  }

  private String str(Map<String, Object> map, String key) {
    Object v = map == null ? null : map.get(key);
    return v == null ? "" : String.valueOf(v);
  }

  private String str(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private String now() {
    return Instant.now().toString();
  }

  private String createId(String prefix) {
    return prefix + "-" + System.currentTimeMillis() + "-" + Integer.toHexString((int) (Math.random() * 0xffffff));
  }

  private Map<String, Object> mapOf(Object... pairs) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) map.put(String.valueOf(pairs[i]), pairs[i + 1]);
    return map;
  }
}
