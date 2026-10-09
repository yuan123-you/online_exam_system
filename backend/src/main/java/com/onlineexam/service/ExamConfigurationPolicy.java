package com.onlineexam.service;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/** Configuration shape follows current INT/DATETIME(3) storage constraints, not guessed business caps. */
public final class ExamConfigurationPolicy {
  private ExamConfigurationPolicy() { }
  public static String paper(Map<String,Object> record) {
    Integer duration = integer(record.get("durationMinutes"));
    if (duration == null || duration <= 0) return "Paper duration must be a positive exact integer.";
    if (record.containsKey("passScore")) {
      Integer threshold = integer(record.get("passScore"));
      if (threshold == null || threshold < 0) return "Pass score must be a nonnegative exact integer.";
    }
    return "";
  }
  public static String exam(Map<String,Object> record) {
    if (record.containsKey("antiCheatLimit")) {
      Integer limit = integer(record.get("antiCheatLimit"));
      if (limit == null || limit < 0) return "Anti-cheat limit must be a nonnegative exact integer.";
    }
    Instant start = instant(record.get("startTime"));
    Instant end = instant(record.get("endTime"));
    if (start == null || end == null || !end.isAfter(start)) return "Exam requires valid start/end times with end after start at millisecond precision.";
    record.put("startTime",start.toString());
    record.put("endTime",end.toString());
    return "";
  }
  private static Integer integer(Object raw) {
    try {
      if (raw instanceof Number) return new BigDecimal(raw.toString()).intValueExact();
      if (raw instanceof String text) return Integer.parseInt(text);
      return null;
    } catch (ArithmeticException | NumberFormatException invalid) { return null; }
  }
  private static Instant instant(Object raw) {
    Instant parsed = parseInstantRaw(raw);
    if (parsed == null) return null;
    try {
      LocalDateTime local = LocalDateTime.ofInstant(parsed, ZoneId.systemDefault());
      if (local.getYear() < 1000 || local.getYear() > 9999) return null;
      return parsed;
    } catch (DateTimeException e) {
      return null;
    }
  }

  private static Instant parseInstantRaw(Object raw) {
    if (raw instanceof java.sql.Timestamp stamp) return stamp.toInstant().truncatedTo(ChronoUnit.MILLIS);
    if (!(raw instanceof String text) || text.isBlank()) return null;
    try { return Instant.parse(text).truncatedTo(ChronoUnit.MILLIS); }
    catch (DateTimeParseException nonInstant) {
      try { return OffsetDateTime.parse(text).toInstant().truncatedTo(ChronoUnit.MILLIS); }
      catch (DateTimeException nonOffset) {
        try {
          return java.sql.Timestamp.valueOf(LocalDateTime.parse(text)).toInstant().truncatedTo(ChronoUnit.MILLIS);
        } catch (Exception invalid) {
          return null;
        }
      }
    }
  }
}
