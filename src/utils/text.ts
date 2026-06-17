/**
 * 文本规范化工具。
 *
 * 历史上后端在部分环境下出现过 UTF-8 被当作 GBK 解读的中文乱码，
 * 曾通过 replacementEntries 映射表在前端运行时修正。
 * 后端已统一使用 UTF-8/utf8mb4 编码（见 application.yml 的
 * characterEncoding=utf8 与 schema.sql 的 DEFAULT CHARSET=utf8mb4），
 * 不再产生此类乱码，因此映射表已移除。
 *
 * 保留 normalizeText / normalizeApiData 两个函数以保持 client.ts
 * 的调用兼容，后续可在确认无外部依赖后安全删除。
 */
export function normalizeText(text: string): string {
  return text ?? "";
}

export function normalizeApiData<T>(value: T, depth: number = 0): T {
  if (depth > 10) return value;
  if (typeof value === "string") {
    return normalizeText(value) as T;
  }
  if (Array.isArray(value)) {
    return value.map((item) => normalizeApiData(item, depth + 1)) as T;
  }
  if (value && typeof value === "object") {
    return Object.fromEntries(
      Object.entries(value as Record<string, unknown>).map(([key, item]) => [key, normalizeApiData(item, depth + 1)])
    ) as T;
  }
  return value;
}
