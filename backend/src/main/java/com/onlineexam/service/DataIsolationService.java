package com.onlineexam.service;

import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 数据隔离服务 - 确保用户只能访问其权限范围内的数据
 * 防止水平越权（教师A访问教师B的数据）和垂直越权
 */
@Service
public class DataIsolationService {

  private final StoreService storeService;

  public DataIsolationService(StoreService storeService) {
    this.storeService = storeService;
  }

  /**
   * 检查用户是否可以访问指定题目
   * - admin: 可以访问所有题目
   * - teacher: 只能访问自己创建的题目
   * - student: 不能直接访问题目（通过考试间接访问）
   */
  public boolean canAccessQuestion(String userId, String questionId) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (user == null) return false;
    if (isAdmin(user)) return true;
    if (isTeacher(user)) {
      Map<String, Object> question = find(store.questions, questionId);
      return question != null && Objects.equals(str(question, "teacherId"), userId);
    }
    return false;
  }

  /**
   * 检查用户是否可以访问指定试卷
   * - admin: 可以访问所有试卷
   * - teacher: 只能访问自己创建的试卷
   * - student: 不能直接访问试卷（通过考试间接访问）
   */
  public boolean canAccessPaper(String userId, String paperId) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (user == null) return false;
    if (isAdmin(user)) return true;
    if (isTeacher(user)) {
      Map<String, Object> paper = find(store.papers, paperId);
      return paper != null && Objects.equals(str(paper, "teacherId"), userId);
    }
    return false;
  }

  /**
   * 检查用户是否可以访问指定考试
   * - admin: 可以访问所有考试
   * - teacher: 只能访问自己创建的考试
   * - student: 只能访问已发布且目标班级包含自己班级的考试
   */
  public boolean canAccessExam(String userId, String examId) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (user == null) return false;
    if (isAdmin(user)) return true;
    Map<String, Object> exam = find(store.exams, examId);
    if (exam == null) return false;
    if (isTeacher(user)) {
      return Objects.equals(str(exam, "teacherId"), userId);
    }
    if (isStudent(user)) {
      return Boolean.TRUE.equals(exam.get("published"))
        && asList(exam.get("targetClassIds")).contains(str(user, "classId"));
    }
    return false;
  }

  /**
   * 检查用户是否可以访问指定提交记录
   * - admin: 可以访问所有提交
   * - teacher: 只能访问自己考试的提交
   * - student: 只能访问自己的提交
   */
  public boolean canAccessSubmission(String userId, String submissionId) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (user == null) return false;
    if (isAdmin(user)) return true;
    Map<String, Object> submission = find(store.submissions, submissionId);
    if (submission == null) return false;
    if (isTeacher(user)) {
      Map<String, Object> exam = find(store.exams, str(submission, "examId"));
      return exam != null && Objects.equals(str(exam, "teacherId"), userId);
    }
    if (isStudent(user)) {
      return Objects.equals(str(submission, "studentId"), userId);
    }
    return false;
  }

  /**
   * 验证用户是否拥有指定题目的所有权
   */
  public boolean ownsQuestion(String userId, String questionId) {
    Store store = storeService.readStore();
    Map<String, Object> question = find(store.questions, questionId);
    return question != null && Objects.equals(str(question, "teacherId"), userId);
  }

  /**
   * 验证用户是否拥有指定试卷的所有权
   */
  public boolean ownsPaper(String userId, String paperId) {
    Store store = storeService.readStore();
    Map<String, Object> paper = find(store.papers, paperId);
    return paper != null && Objects.equals(str(paper, "teacherId"), userId);
  }

  /**
   * 验证用户是否拥有指定考试的所有权
   */
  public boolean ownsExam(String userId, String examId) {
    Store store = storeService.readStore();
    Map<String, Object> exam = find(store.exams, examId);
    return exam != null && Objects.equals(str(exam, "teacherId"), userId);
  }

  /**
   * 验证教师是否可以管理指定考试（拥有所有权或是管理员）
   */
  public boolean canManageExam(String userId, String examId) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (user == null) return false;
    if (isAdmin(user)) return true;
    return ownsExam(userId, examId);
  }

  /**
   * 验证学生是否可以访问指定错题本条目
   */
  public boolean canAccessWrongBookEntry(String userId, String entryId) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (user == null) return false;
    if (isAdmin(user)) return true;
    Map<String, Object> entry = find(store.wrongBookEntries, entryId);
    if (entry == null) return false;
    if (isStudent(user)) {
      return Objects.equals(str(entry, "studentId"), userId);
    }
    return false;
  }

  /**
   * 验证用户是否为管理员
   */
  public boolean isAdmin(String userId) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    return isAdmin(user);
  }

  /**
   * 验证用户是否为教师
   */
  public boolean isTeacher(String userId) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    return isTeacher(user);
  }

  /**
   * 验证用户是否为学生
   */
  public boolean isStudent(String userId) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    return isStudent(user);
  }

  private boolean isAdmin(Map<String, Object> user) {
    return isRole(user, "admin");
  }

  private boolean isTeacher(Map<String, Object> user) {
    return isRole(user, "teacher");
  }

  private boolean isStudent(Map<String, Object> user) {
    return isRole(user, "student");
  }

  private boolean isRole(Map<String, Object> user, String role) {
    return user != null && Objects.equals(str(user, "role"), role);
  }

  private Map<String, Object> find(List<Map<String, Object>> rows, String id) {
    if (id == null) return null;
    return rows.stream().filter(row -> Objects.equals(str(row, "id"), id)).findFirst().orElse(null);
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
}
