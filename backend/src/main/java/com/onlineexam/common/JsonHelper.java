package com.onlineexam.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * JSON 序列化和反序列化工具类
 */
@Component
public class JsonHelper {
  private final ObjectMapper mapper;
  private final ZoneId zone = ZoneId.systemDefault();

  public JsonHelper(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  /**
   * 从数据库 JSON 字符串读取 List
   */
  public List<Object> readList(Object raw) {
    if (raw == null) return new ArrayList<>();
    try {
      return mapper.readValue(String.valueOf(raw), new TypeReference<List<Object>>() {});
    } catch (Exception e) {
      return new ArrayList<>();
    }
  }

  /**
   * 从数据库 JSON 字符串读取 Map
   */
  public Map<String, Object> readMap(Object raw) {
    if (raw == null) return new LinkedHashMap<>();
    try {
      return mapper.readValue(String.valueOf(raw), new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      return new LinkedHashMap<>();
    }
  }

  /**
   * 将对象序列化为 JSON 字符串
   */
  public String json(Object value) {
    try {
      Object next = value == null ? List.of() : value;
      return mapper.writeValueAsString(next);
    } catch (Exception e) {
      return "[]";
    }
  }

  /**
   * 将对象转换为 Timestamp
   */
  public Timestamp timestamp(Object value) {
    if (value == null || String.valueOf(value).isBlank()) return null;
    if (value instanceof Timestamp t) return t;
    try {
      Instant instant = Instant.parse(String.valueOf(value));
      return Timestamp.from(instant);
    } catch (DateTimeParseException ignored) {
      try {
        return Timestamp.valueOf(LocalDateTime.parse(String.valueOf(value).replace("Z", "")));
      } catch (Exception e) {
        return null;
      }
    }
  }

  /**
   * 将数据库时间值转换为 ISO 格式字符串
   */
  public String asIso(Object value) {
    if (value == null) return null;
    if (value instanceof Timestamp ts) return ts.toInstant().toString();
    if (value instanceof LocalDateTime dt) return dt.atZone(zone).toInstant().toString();
    return String.valueOf(value);
  }
}
