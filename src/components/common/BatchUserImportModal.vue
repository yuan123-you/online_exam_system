<template>
  <BaseModal @close="$emit('close')">
    <template #header>
      <div>
        <h3>{{ isStudent ? "批量导入学生" : "批量导入教师" }}</h3>
        <p class="muted">支持上传 Excel .xlsx、CSV/TXT，或直接粘贴从表格复制的内容。{{ isStudent ? "学生列：" : "教师列：" }}{{ templateHeaderText }}</p>
      </div>
    </template>

    <div class="modal-toolbar">
      <button class="ghost-btn" type="button" @click="downloadTemplate">下载模板</button>
    </div>

    <form class="form-grid" @submit.prevent="submitImport">
      <label>
        <span>导入文件</span>
        <input ref="fileInput" type="file" accept=".xlsx,.csv,.txt" @change="onFileChange" />
      </label>
      <label>
        <span>{{ isStudent ? "学生名单" : "教师名单" }}</span>
        <textarea v-model="payload" :placeholder="placeholderText" @input="onPayloadInput" />
      </label>

      <div class="import-preview">
        <template v-if="parsedRecords.length === 0 && parsedErrors.length === 0">
          <div class="empty-state">等待输入{{ isStudent ? "学生" : "教师" }}数据后自动预校验</div>
        </template>
        <template v-else>
          <div class="preview-grid">
            <article class="mini-item">
              <h4>预检结果</h4>
              <p>可导入 {{ parsedRecords.length }} 名{{ isStudent ? "学生" : "教师" }}，待修正 {{ parsedErrors.length }} 行。</p>
            </article>
            <article class="mini-item">
              <h4>导入说明</h4>
              <p>{{ isStudent ? "班级列支持填写班级 ID 或班级名称。" : "院系列支持填写院系 ID 或院系名称。" }}密码为空时默认使用 123456。</p>
            </article>
          </div>
          <template v-if="parsedErrors.length > 0">
            <div class="import-error-list">
              <p v-for="(err, i) in parsedErrors.slice(0, 6)" :key="i">{{ formatError(err) }}</p>
            </div>
          </template>
          <template v-else>
            <p class="toolbar-note">未发现格式错误，可直接提交。</p>
          </template>
        </template>
      </div>

      <div class="action-row">
        <button class="ghost-btn" type="button" @click="$emit('close')">取消</button>
        <button class="primary-btn" type="submit" :disabled="submitting">
          {{ submitting ? "导入中..." : "开始导入" }}
        </button>
      </div>
    </form>
  </BaseModal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import BaseModal from "./BaseModal.vue";
import { batchImportUsers, type BatchImportResult } from "../../api/client";
import type { BootstrapData } from "../../types";

type ImportError = { lineNumber?: number; message: string };

const props = defineProps<{
  role: "student" | "teacher";
  bootstrap: BootstrapData;
}>();

const emit = defineEmits<{
  close: [];
  success: [result: BatchImportResult];
}>();

const isStudent = computed(() => props.role === "student");

const templateHeaderText = computed(() =>
  isStudent.value ? "学号|姓名|班级|专业|密码" : "账号|姓名|院系|密码"
);

const placeholderText = computed(() =>
  isStudent.value
    ? "2023003|王五|2310|软件工程|123456\n2023004|赵六|class-1|软件工程|123456"
    : "t1001|李老师|计算机学院|123456\nt1002|周老师|dept-1|123456"
);

const payload = ref("");
const fileInput = ref<HTMLInputElement | null>(null);
const fileRows = ref<string[][]>([]);
const fileErrors = ref<ImportError[]>([]);
const submitting = ref(false);

const classMap = computed(() => {
  const map = new Map<string, string>();
  props.bootstrap.classes.forEach((item) => {
    map.set(normalizeKey(item.id), item.id);
    map.set(normalizeKey(item.name), item.id);
  });
  return map;
});

const departmentMap = computed(() => {
  const map = new Map<string, string>();
  props.bootstrap.departments.forEach((item) => {
    map.set(normalizeKey(item.id), item.id);
    map.set(normalizeKey(item.name), item.id);
  });
  return map;
});

const parsedRecords = computed(() => parseRows().records);
const parsedErrors = computed(() => {
  const result = parseRows();
  return [...fileErrors.value, ...result.errors];
});

function normalizeKey(value: string): string {
  return String(value ?? "").trim().toLowerCase();
}

function isHeaderRow(cells: string[]): boolean {
  const joined = normalizeKey(cells.join("|"));
  return (
    (joined.includes("username") ||
      joined.includes("学号") ||
      joined.includes("账号") ||
      joined.includes("姓名")) &&
    (joined.includes("班级") ||
      joined.includes("院系") ||
      joined.includes("class") ||
      joined.includes("department"))
  );
}

function parseRows(): { records: Array<Record<string, unknown>>; errors: ImportError[] } {
  const rows =
    fileRows.value.length > 0
      ? fileRows.value
      : rowsFromDelimitedText(payload.value);
  const records: Array<Record<string, unknown>> = [];
  const errors: ImportError[] = [];

  rows.forEach((row, index) => {
    const lineNumber = index + 1;
    const cells = row.map((item) => String(item ?? "").trim());
    if (!cells.some(Boolean) || isHeaderRow(cells)) return;

    if (isStudent.value) {
      if (cells.length < 2) {
        errors.push({ lineNumber, message: "格式应为：学号|班级，或 学号|姓名|班级|专业|密码" });
        return;
      }
      const username = cells[0];
      const secondIsClass = classMap.value.has(normalizeKey(cells[1]));
      const classText = secondIsClass ? cells[1] : cells[2];
      const name = secondIsClass ? username : cells[1] || username;
      const classId = classMap.value.get(normalizeKey(classText));
      if (!classId) {
        errors.push({ lineNumber, message: `未找到班级：${classText}` });
        return;
      }
      const classRecord = props.bootstrap.classes.find((item) => item.id === classId);
      const major = (secondIsClass ? cells[2] : cells[3]) || classRecord?.major || "";
      const password = (secondIsClass ? cells[3] : cells[4]) || "123456";
      if (!username || !classText) {
        errors.push({ lineNumber, message: "学号、班级不能为空" });
        return;
      }
      records.push({ role: "student", username, name, classId, major, password });
    } else {
      if (cells.length < 2) {
        errors.push({ lineNumber, message: "格式应为：账号|院系，或 账号|姓名|院系|密码" });
        return;
      }
      const username = cells[0];
      const secondIsDepartment = departmentMap.value.has(normalizeKey(cells[1]));
      const name = secondIsDepartment ? username : cells[1] || username;
      const departmentText = secondIsDepartment ? cells[1] : cells[2];
      const password = (secondIsDepartment ? cells[2] : cells[3]) || "123456";
      const departmentId = departmentMap.value.get(normalizeKey(departmentText));
      if (!username || !departmentText) {
        errors.push({ lineNumber, message: "账号、院系不能为空" });
        return;
      }
      if (!departmentId) {
        errors.push({ lineNumber, message: `未找到院系：${departmentText}` });
        return;
      }
      records.push({ role: "teacher", username, name, departmentId, password: password || "123456" });
    }
  });

  return { records, errors };
}

function rowsFromDelimitedText(text: string): string[][] {
  return String(text || "")
    .split(/\r?\n/)
    .map((line) => parseDelimitedLine(line))
    .filter((row) => row.some((cell) => cell.trim()));
}

function parseDelimitedLine(line: string): string[] {
  const text = String(line || "");
  if (text.includes("\t")) return text.split("\t").map((item) => item.trim());
  if (text.includes("|")) return text.split("|").map((item) => item.trim());
  return parseCsvLine(text).map((item) => item.trim());
}

function parseCsvLine(line: string): string[] {
  const cells: string[] = [];
  let current = "";
  let quoted = false;
  for (let i = 0; i < line.length; i++) {
    const char = line[i];
    if (char === '"') {
      if (quoted && line[i + 1] === '"') {
        current += '"';
        i++;
      } else {
        quoted = !quoted;
      }
    } else if (char === "," && !quoted) {
      cells.push(current);
      current = "";
    } else {
      current += char;
    }
  }
  cells.push(current);
  return cells;
}

function rowsToPreviewText(rows: string[][]): string {
  return rows.slice(0, 50).map((row) => row.join("|")).join("\n");
}

async function onFileChange() {
  fileRows.value = [];
  fileErrors.value = [];
  try {
    const file = fileInput.value?.files?.[0];
    if (file) {
      fileRows.value = await readImportFileRows(file);
      payload.value = rowsToPreviewText(fileRows.value);
    }
  } catch (error: any) {
    fileErrors.value = [{ message: error?.message || "文件解析失败" }];
  }
}

function onPayloadInput() {
  if (payload.value.trim()) {
    fileRows.value = [];
    fileErrors.value = [];
    if (fileInput.value) fileInput.value.value = "";
  }
}

async function readImportFileRows(file: File): Promise<string[][]> {
  const name = file.name.toLowerCase();
  if (name.endsWith(".xlsx")) {
    return readXlsxRows(await file.arrayBuffer());
  }
  return rowsFromDelimitedText(await file.text());
}

async function readXlsxRows(arrayBuffer: ArrayBuffer): Promise<string[][]> {
  const entries = await unzipEntries(arrayBuffer);
  const sharedStrings = parseSharedStrings(entries.get("xl/sharedStrings.xml") || "");
  const sheetPath = getFirstWorksheetPath(entries) || "xl/worksheets/sheet1.xml";
  const sheetXml = entries.get(sheetPath);
  if (!sheetXml) throw new Error("Excel 文件中未找到工作表");
  return parseWorksheetRows(sheetXml, sharedStrings);
}

async function unzipEntries(arrayBuffer: ArrayBuffer): Promise<Map<string, string>> {
  const bytes = new Uint8Array(arrayBuffer);
  const view = new DataView(arrayBuffer);
  const eocdOffset = findZipEndOfCentralDirectory(view);
  if (eocdOffset < 0) throw new Error("不是有效的 .xlsx 文件");
  const entryCount = view.getUint16(eocdOffset + 10, true);
  let offset = view.getUint32(eocdOffset + 16, true);
  const entries = new Map<string, string>();
  const decoder = new TextDecoder("utf-8");

  for (let index = 0; index < entryCount; index++) {
    if (view.getUint32(offset, true) !== 0x02014b50) break;
    const method = view.getUint16(offset + 10, true);
    const compressedSize = view.getUint32(offset + 20, true);
    const nameLength = view.getUint16(offset + 28, true);
    const extraLength = view.getUint16(offset + 30, true);
    const commentLength = view.getUint16(offset + 32, true);
    const localOffset = view.getUint32(offset + 42, true);
    const name = decoder.decode(bytes.slice(offset + 46, offset + 46 + nameLength)).replace(/^\/+/, "");
    const localNameLength = view.getUint16(localOffset + 26, true);
    const localExtraLength = view.getUint16(localOffset + 28, true);
    const dataStart = localOffset + 30 + localNameLength + localExtraLength;
    const compressed = bytes.slice(dataStart, dataStart + compressedSize);
    entries.set(name, await inflateZipEntry(compressed, method));
    offset += 46 + nameLength + extraLength + commentLength;
  }

  return entries;
}

function findZipEndOfCentralDirectory(view: DataView): number {
  for (let offset = view.byteLength - 22; offset >= Math.max(0, view.byteLength - 66000); offset--) {
    if (view.getUint32(offset, true) === 0x06054b50) return offset;
  }
  return -1;
}

async function inflateZipEntry(bytes: Uint8Array, method: number): Promise<string> {
  if (method === 0) return new TextDecoder("utf-8").decode(bytes);
  if (method !== 8 || typeof DecompressionStream === "undefined") {
    throw new Error("当前浏览器不支持解析该 Excel 压缩格式");
  }
  const stream = new Blob([new Uint8Array(bytes)]).stream().pipeThrough(new DecompressionStream("deflate-raw"));
  return await new Response(stream).text();
}

function parseSharedStrings(xml: string): string[] {
  if (!xml) return [];
  const doc = new DOMParser().parseFromString(xml, "application/xml");
  return [...doc.getElementsByTagName("si")].map((item) =>
    [...item.getElementsByTagName("t")].map((node) => node.textContent || "").join("")
  );
}

function getFirstWorksheetPath(entries: Map<string, string>): string | null {
  const workbookXml = entries.get("xl/workbook.xml");
  const relsXml = entries.get("xl/_rels/workbook.xml.rels");
  if (!workbookXml || !relsXml) {
    return [...entries.keys()].find((key) => /^xl\/worksheets\/sheet\d+\.xml$/i.test(key)) || null;
  }
  const workbook = new DOMParser().parseFromString(workbookXml, "application/xml");
  const firstSheet = workbook.getElementsByTagName("sheet")[0];
  const relId = firstSheet?.getAttribute("r:id") || firstSheet?.getAttribute("id");
  const rels = new DOMParser().parseFromString(relsXml, "application/xml");
  const relation = [...rels.getElementsByTagName("Relationship")].find((item) => item.getAttribute("Id") === relId);
  const target = relation?.getAttribute("Target");
  if (!target) return null;
  return target.startsWith("/") ? target.slice(1) : normalizeZipPath(`xl/${target}`);
}

function normalizeZipPath(p: string): string {
  const parts: string[] = [];
  String(p || "")
    .split("/")
    .forEach((part) => {
      if (!part || part === ".") return;
      if (part === "..") { parts.pop(); return; }
      parts.push(part);
    });
  return parts.join("/");
}

function parseWorksheetRows(xml: string, sharedStrings: string[]): string[][] {
  const doc = new DOMParser().parseFromString(xml, "application/xml");
  return [...doc.getElementsByTagName("row")]
    .map((row) => {
      const cells: string[] = [];
      [...row.getElementsByTagName("c")].forEach((cell) => {
        const ref = cell.getAttribute("r") || "";
        const columnIndex = columnNameToIndex(ref.replace(/[0-9]/g, ""));
        cells[columnIndex] = readWorksheetCell(cell, sharedStrings);
      });
      return cells.map((item) => String(item ?? "").trim());
    })
    .filter((row) => row.some(Boolean));
}

function readWorksheetCell(cell: Element, sharedStrings: string[]): string {
  const type = cell.getAttribute("t");
  if (type === "inlineStr") {
    return [...cell.getElementsByTagName("t")].map((node) => node.textContent || "").join("");
  }
  const value = cell.getElementsByTagName("v")[0]?.textContent || "";
  return type === "s" ? sharedStrings[Number(value)] || "" : value;
}

function columnNameToIndex(name: string): number {
  let index = 0;
  for (const char of name) {
    index = index * 26 + char.toUpperCase().charCodeAt(0) - 64;
  }
  return Math.max(index - 1, 0);
}

function downloadTemplate() {
  const template = isStudent.value
    ? ["学号|姓名|班级|专业|密码", "2023003|王五|2310|软件工程|123456", "2023004|赵六|class-1|软件工程|123456"].join("\n")
    : ["账号|姓名|院系|密码", "t1001|李老师|dept-1|123456", "t1002|周老师|计算机学院|123456"].join("\n");
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, "0");
  const timestamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
  const filename = `${isStudent.value ? "student" : "teacher"}-import-template-${timestamp}.csv`;
  const blob = new Blob(["\uFEFF" + template], { type: "text/csv;charset=utf-8" });
  const link = document.createElement("a");
  link.href = URL.createObjectURL(blob);
  link.download = filename;
  link.click();
  URL.revokeObjectURL(link.href);
}

function formatError(err: ImportError): string {
  return err.lineNumber ? `第 ${err.lineNumber} 行：${err.message}` : err.message;
}

async function submitImport() {
  const { records } = parseRows();
  const allErrors = [...fileErrors.value, ...parseRows().errors];
  if (!records.length) {
    alert(allErrors.length ? allErrors.map(formatError).join("\n") : `没有可导入的${isStudent.value ? "学生" : "教师"}`);
    return;
  }
  submitting.value = true;
  try {
    const result = await batchImportUsers(records);
    const messages = [`已导入 ${result.importedCount} 名${isStudent.value ? "学生" : "教师"}`];
    const errorLines = [
      ...allErrors.map(formatError),
      ...(result.errors || []).map((item) => `${item.title || (isStudent.value ? "学生" : "教师")}：${item.message}`),
    ];
    if (errorLines.length) {
      messages.push(`未导入 ${errorLines.length} 条：\n${errorLines.slice(0, 8).join("\n")}${errorLines.length > 8 ? "\n..." : ""}`);
    }
    alert(messages.join("\n\n"));
    emit("success", result);
    emit("close");
  } catch (error: any) {
    alert(error?.message || "批量导入失败");
  } finally {
    submitting.value = false;
  }
}

watch(
  () => [props.role, props.bootstrap],
  () => {
    payload.value = "";
    fileRows.value = [];
    fileErrors.value = [];
    if (fileInput.value) fileInput.value.value = "";
  }
);
</script>
