package com.onlineexam.common;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 公共工具类 — 消除全项目重复的工具方法
 * 统一 str, find, isRole, mapOf, asInt, asBool, asList, asMap, error, createId, compact 等方法
 */
public final class StoreHelper {

    private StoreHelper() {}

    /** 从 Map 中安全获取字符串值，null 返回空字符串 */
    public static String str(Map<String, Object> map, String key) {
        return str(map == null ? null : map.get(key));
    }

    /** 将对象转换为字符串，null 返回空字符串 */
    public static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** 将对象转换为整数，null 或格式错误返回 0 */
    public static int asInt(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof Boolean) return 0;
        if (value == null || String.valueOf(value).isBlank()) return 0;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 将对象转换为布尔值，统一处理 Boolean/Number/String */
    public static boolean asBool(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() != 0;
        if (value != null) {
            try { return Integer.parseInt(String.valueOf(value)) != 0; } catch (NumberFormatException ignored) {}
        }
        return false;
    }

    /** 从 Map 中获取布尔值 */
    public static boolean asBool(Map<String, Object> map, String key) {
        return asBool(map == null ? null : map.get(key));
    }

    /** 将对象转换为 List，null 或格式错误返回空列表 */
    @SuppressWarnings("unchecked")
    public static List<Object> asList(Object value) {
        if (value instanceof List<?> list) return (List<Object>) list;
        if (value == null) return new ArrayList<>();
        if (value instanceof String s && s.isBlank()) return new ArrayList<>();
        return new ArrayList<>();
    }

    /** 将对象转换为 Map，null 或格式错误返回空 Map */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        return new LinkedHashMap<>();
    }

    /** 在列表中按 id 查找元素 */
    public static Map<String, Object> find(List<Map<String, Object>> list, String id) {
        if (list == null || id == null) return null;
        return list.stream()
            .filter(m -> id.equals(str(m, "id")))
            .findFirst()
            .orElse(null);
    }

    /** 检查用户是否具有指定角色 */
    public static boolean isRole(Map<String, Object> user, String role) {
        return user != null && Objects.equals(str(user, "role"), role);
    }

    /**
     * 构建键值对 Map，校验参数个数必须为偶数
     * 修复原 mapOf 奇数参数导致 ArrayIndexOutOfBoundsException 的 Bug
     */
    public static Map<String, Object> mapOf(Object... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("mapOf 参数个数必须为偶数，当前为 " + pairs.length);
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }

    /** 构建错误响应 */
    public static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(mapOf("message", message));
    }

    /** 生成带前缀的唯一 ID */
    public static String createId(String prefix) {
        return prefix + "-" + System.currentTimeMillis() + "-" + Integer.toHexString((int) (Math.random() * 0xffffff));
    }

    /** nullableStr：空字符串转为 null（用于数据库写入） */
    public static String nullableStr(Map<String, Object> r, String key) {
        String value = str(r, key);
        return value.isBlank() ? null : value;
    }

    /**
     * 移除 Map 中值为 null 或空字符串的条目（不修改原 Map，返回新 Map）
     * 修复原 compact 方法修改传入 Map 的副作用 Bug
     */
    public static Map<String, Object> compact(Map<String, Object> source) {
        if (source == null) return new LinkedHashMap<>();
        Map<String, Object> result = new LinkedHashMap<>(source);
        result.entrySet().removeIf(e -> e.getValue() == null || "".equals(e.getValue()));
        return result;
    }

    /** 统一的状态规范化方法，使用精确匹配替代 contains 避免误匹配 */
    public static String normalizeStatus(String status) {
        if (status == null) return "";
        return switch (status) {
            case "已完成", "完成" -> "已完成";
            case "待阅卷" -> "待阅卷";
            case "进行中" -> "进行中";
            case "已结束" -> "已结束";
            default -> status;
        };
    }
}
