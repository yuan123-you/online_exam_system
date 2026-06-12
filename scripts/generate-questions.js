/**
 * generate-questions.js
 *
 * Generates SQL INSERT statements for batch-importing questions into the database.
 * Ensures every subject has at least 30 questions of each type.
 *
 * Usage: node generate-questions.js
 * Output: bulk-questions.sql (in the same directory as this script)
 */

const fs = require("fs");
const path = require("path");

// ─── Configuration ────────────────────────────────────────────────────────────

const TEACHER_ID = "teacher-1";
const SOURCE_TAG = "bulk-gen-20260610";
const MIN_PER_TYPE = 30;
const OUTPUT_FILE = path.join(__dirname, "bulk-questions.sql");

const SCORE_MAP = {
  single: 4,
  multiple: 5,
  judge: 3,
  fill: 4,
  short: 10,
  coding: 15,
};

// ─── Current data state ──────────────────────────────────────────────────────

const currentState = {
  "Java \u57FA\u7840":  { single: 66, multiple: 24, judge: 0, fill: 0, short: 0, coding: 0 },
  Web:           { single: 60, multiple: 24, judge: 0, fill: 0, short: 0, coding: 0 },
  "\u524D\u7AEF":          { single: 60, multiple: 24, judge: 0, fill: 0, short: 0, coding: 0 },
  "\u6570\u636E\u5E93":        { single: 50, multiple: 20, judge: 1, fill: 0, short: 0, coding: 0 },
  "\u8F6F\u4EF6\u5DE5\u7A0B":      { single: 10, multiple: 4,  judge: 0, fill: 0, short: 0, coding: 0 },
  "\u5728\u7EBF\u8003\u8BD5":      { single: 10, multiple: 4,  judge: 0, fill: 0, short: 0, coding: 0 },
  "Spring MVC":  { single: 10, multiple: 4,  judge: 0, fill: 0, short: 0, coding: 0 },
  JVM:           { single: 40, multiple: 16, judge: 0, fill: 0, short: 6, coding: 6 },
  "Java \u5E76\u53D1":   { single: 60, multiple: 24, judge: 0, fill: 0, short: 3, coding: 8 },
  "Java \u96C6\u5408":   { single: 60, multiple: 24, judge: 0, fill: 0, short: 0, coding: 1 },
  Spring:        { single: 50, multiple: 20, judge: 0, fill: 0, short: 4, coding: 4 },
  MySQL:         { single: 20, multiple: 8,  judge: 0, fill: 0, short: 6, coding: 6 },
  CSS3:          { single: 5,  multiple: 5,  judge: 5, fill: 5, short: 5, coding: 5 },
  HTML5:         { single: 5,  multiple: 5,  judge: 5, fill: 5, short: 5, coding: 5 },
  TypeScript:    { single: 5,  multiple: 5,  judge: 5, fill: 5, short: 5, coding: 5 },
  "Vue 2":       { single: 5,  multiple: 5,  judge: 5, fill: 5, short: 5, coding: 5 },
  "Vue 3":       { single: 5,  multiple: 5,  judge: 5, fill: 5, short: 5, coding: 5 },
  "Node.js":     { single: 5,  multiple: 5,  judge: 5, fill: 5, short: 5, coding: 5 },
  JavaScript:    { single: 5,  multiple: 5,  judge: 5, fill: 5, short: 5, coding: 5 },
  ECharts:       { single: 5,  multiple: 5,  judge: 5, fill: 5, short: 5, coding: 5 },
  MyBatis:       { single: 5,  multiple: 5,  judge: 5, fill: 5, short: 5, coding: 5 },
  "Spring Boot": { single: 5,  multiple: 5,  judge: 5, fill: 5, short: 5, coding: 5 },
  "\u524D\u7AEF\u5F00\u53D1":      { single: 5,  multiple: 5,  judge: 5, fill: 5, short: 5, coding: 5 },
  Java:          { single: 5,  multiple: 5,  judge: 10, fill: 10, short: 10, coding: 4 },
  "Java Web":    { single: 6,  multiple: 6,  judge: 10, fill: 10, short: 10, coding: 10 },
  "\u8BA1\u7B97\u673A\u7F51\u7EDC":    { single: 5,  multiple: 5,  judge: 5, fill: 5, short: 5, coding: 5 },
};

// ─── Subject codes for ID generation ──────────────────────────────────────────

const subjectCodes = {
  "Java \u57FA\u7840": "java-base", "Java \u96C6\u5408": "java-coll", "Java \u5E76\u53D1": "java-conc",
  Java: "java", "Java Web": "javaweb", JVM: "jvm",
  Spring: "spring", "Spring MVC": "smvc", "Spring Boot": "sboot",
  MyBatis: "mybatis", MySQL: "mysql", "\u6570\u636E\u5E93": "db",
  Web: "web", "\u524D\u7AEF": "fe", "\u524D\u7AEF\u5F00\u53D1": "fe-dev",
  JavaScript: "js", TypeScript: "ts", HTML5: "html5", CSS3: "css3",
  "Vue 2": "vue2", "Vue 3": "vue3", "Node.js": "nodejs",
  ECharts: "echarts", "\u8F6F\u4EF6\u5DE5\u7A0B": "se", "\u5728\u7EBF\u8003\u8BD5": "exam",
  "\u8BA1\u7B97\u673A\u7F51\u7EDC": "net",
};

// ─── Knowledge points per subject ─────────────────────────────────────────────

const knowledgePoints = {
  "Java \u57FA\u7840": ["\u9762\u5411\u5BF9\u8C61","\u7EE7\u627F\u4E0E\u591A\u6001","\u63A5\u53E3\u4E0E\u62BD\u8C61\u7C7B","Lambda\u8868\u8FBE\u5F0F","\u53CD\u5C04\u673A\u5236","\u6CE8\u89E3","IO\u6D41","\u5E8F\u5217\u5316","\u6B63\u5219\u8868\u8FBE\u5F0F","\u65E5\u671F\u65F6\u95F4API"],
  "Java \u96C6\u5408": ["ArrayList","LinkedList","HashMap","TreeMap","HashSet","TreeSet","Iterator","Comparator","ConcurrentHashMap","Collections\u5DE5\u5177\u7C7B"],
  "Java \u5E76\u53D1": ["Thread","Runnable","synchronized","volatile","Lock","Condition","\u7EBF\u7A0B\u6C60","CompletableFuture","Atomic\u7C7B","ThreadLocal"],
  JVM: ["\u7C7B\u52A0\u8F7D\u673A\u5236","\u5185\u5B58\u6A21\u578B","\u5783\u573E\u56DE\u6536","GC\u7B97\u6CD5","\u5806\u6808\u7ED3\u6784","JIT\u7F16\u8BD1","\u5B57\u8282\u7801","\u7C7B\u521D\u59CB\u5316","\u5185\u5B58\u6CC4\u6F0F","JVM\u8C03\u4F18"],
  "\u6570\u636E\u5E93": ["\u5173\u7CFB\u4EE3\u6570","SQL\u8BED\u6CD5","\u7D22\u5F15","\u4E8B\u52A1","\u8303\u5F0F","\u89C6\u56FE","\u5B58\u50A8\u8FC7\u7A0B","\u89E6\u53D1\u5668","\u5E76\u53D1\u63A7\u5236","\u6570\u636E\u5E93\u8BBE\u8BA1"],
  MySQL: ["InnoDB\u5F15\u64CE","\u7D22\u5F15\u4F18\u5316","\u6162\u67E5\u8BE2","\u4E3B\u4ECE\u590D\u5236","\u5206\u5E93\u5206\u8868","\u4E8B\u52A1\u9694\u79BB\u7EA7\u522B","MVCC","\u9501\u673A\u5236","Explain\u5206\u6790","binlog"],
  Web: ["HTTP\u534F\u8BAE","RESTful API","Cookie\u4E0ESession","JWT","CORS","HTTPS","WebSocket","\u72B6\u6001\u7801","\u8BF7\u6C42\u65B9\u6CD5","\u7F13\u5B58\u7B56\u7565"],
  Spring: ["IoC\u5BB9\u5668","AOP","Bean\u751F\u547D\u5468\u671F","\u4E8B\u52A1\u7BA1\u7406","\u4F9D\u8D56\u6CE8\u5165","\u4E8B\u4EF6\u673A\u5236","\u6761\u4EF6\u6CE8\u89E3","Profile","SpEL","\u5FAA\u73AF\u4F9D\u8D56"],
  "Spring MVC": ["\u63A7\u5236\u5668","\u8BF7\u6C42\u6620\u5C04","\u62E6\u622A\u5668","\u5F02\u5E38\u5904\u7406","\u6570\u636E\u7ED1\u5B9A","\u89C6\u56FE\u89E3\u6790","JSON\u8F6C\u6362","\u6587\u4EF6\u4E0A\u4F20","\u8DE8\u57DF\u914D\u7F6E","\u53C2\u6570\u6821\u9A8C"],
  "Spring Boot": ["\u81EA\u52A8\u914D\u7F6E","Starter","Actuator","\u914D\u7F6E\u5C5E\u6027","\u6761\u4EF6\u88C5\u914D","\u542F\u52A8\u6D41\u7A0B","\u5185\u5D4C\u5BB9\u5668","\u65E5\u5FD7\u914D\u7F6E","\u5B89\u5168\u96C6\u6210","\u90E8\u7F72\u6253\u5305"],
  MyBatis: ["\u6620\u5C04\u6587\u4EF6","\u52A8\u6001SQL","\u7F13\u5B58\u673A\u5236","\u63D2\u4EF6\u5F00\u53D1","\u7ED3\u679C\u6620\u5C04","\u5206\u9875\u67E5\u8BE2","\u6279\u91CF\u64CD\u4F5C","\u7C7B\u578B\u5904\u7406\u5668","\u5173\u8054\u67E5\u8BE2","\u9006\u5411\u5DE5\u7A0B"],
  "\u524D\u7AEF": ["DOM\u64CD\u4F5C","\u4E8B\u4EF6\u673A\u5236","CSS\u5E03\u5C40","\u54CD\u5E94\u5F0F\u8BBE\u8BA1","\u6D4F\u89C8\u5668\u6E32\u67D3","\u6027\u80FD\u4F18\u5316","\u6A21\u5757\u5316","\u5305\u7BA1\u7406","\u524D\u7AEF\u5DE5\u7A0B\u5316","\u8DE8\u57DF\u65B9\u6848"],
  "\u524D\u7AEF\u5F00\u53D1": ["Webpack","Babel","ESLint","\u7EC4\u4EF6\u5316","\u72B6\u6001\u7BA1\u7406","\u8DEF\u7531","\u6784\u5EFA\u4F18\u5316","\u5355\u5143\u6D4B\u8BD5","CI/CD","\u4EE3\u7801\u89C4\u8303"],
  JavaScript: ["\u95ED\u5305","\u539F\u578B\u94FE","Promise","async/await","\u4E8B\u4EF6\u5FAA\u73AF","\u4F5C\u7528\u57DF\u94FE","this\u6307\u5411","\u89E3\u6784\u8D4B\u503C","\u6A21\u5757\u5316","\u9519\u8BEF\u5904\u7406"],
  TypeScript: ["\u7C7B\u578B\u7CFB\u7EDF","\u63A5\u53E3","\u6CDB\u578B","\u88C5\u9970\u5668","\u679A\u4E3E","\u7C7B\u578B\u5B88\u536B","\u547D\u540D\u7A7A\u95F4","\u58F0\u660E\u6587\u4EF6","\u7C7B\u578B\u63A8\u65AD","\u5DE5\u5177\u7C7B\u578B"],
  HTML5: ["\u8BED\u4E49\u5316\u6807\u7B7E","Canvas","SVG","\u672C\u5730\u5B58\u50A8","Web Workers","Geolocation","\u97F3\u89C6\u9891","\u8868\u5355\u589E\u5F3A","\u62D6\u653EAPI","History API"],
  CSS3: ["Flexbox","Grid","\u52A8\u753B","\u8FC7\u6E21","\u53D8\u6362","\u5A92\u4F53\u67E5\u8BE2","\u53D8\u91CF","\u4F2A\u5143\u7D20","\u9009\u62E9\u5668","\u76D2\u6A21\u578B"],
  "Vue 2": ["\u7EC4\u4EF6","\u6307\u4EE4","\u8BA1\u7B97\u5C5E\u6027","\u751F\u547D\u5468\u671F","Vuex","Vue Router","Mixin","\u8FC7\u6EE4\u5668","\u63D2\u69FD","\u54CD\u5E94\u5F0F\u539F\u7406"],
  "Vue 3": ["Composition API","ref\u4E0Ereactive","setup\u51FD\u6570","\u751F\u547D\u5468\u671F\u94A9\u5B50","Pinia","Teleport","Suspense","\u81EA\u5B9A\u4E49\u6307\u4EE4","\u6A21\u677F\u5F15\u7528","\u6027\u80FD\u4F18\u5316"],
  "Node.js": ["\u4E8B\u4EF6\u9A71\u52A8","Stream","Buffer","\u6A21\u5757\u7CFB\u7EDF","Express","\u4E2D\u95F4\u4EF6","\u6587\u4EF6\u64CD\u4F5C","\u8FDB\u7A0B\u7BA1\u7406","\u9519\u8BEF\u5904\u7406","\u5305\u7BA1\u7406"],
  ECharts: ["\u56FE\u8868\u914D\u7F6E","\u6570\u636E\u7CFB\u5217","\u5750\u6807\u8F74","\u4EA4\u4E92\u4E8B\u4EF6","\u4E3B\u9898\u5B9A\u5236","\u6570\u636E\u53EF\u89C6\u5316","\u52A8\u6001\u66F4\u65B0","\u54CD\u5E94\u5F0F","\u5730\u56FE","\u4EEA\u8868\u76D8"],
  "\u8F6F\u4EF6\u5DE5\u7A0B": ["\u9700\u6C42\u5206\u6790","\u8BBE\u8BA1\u6A21\u5F0F","UML\u5EFA\u6A21","\u6D4B\u8BD5\u65B9\u6CD5","\u9879\u76EE\u7BA1\u7406","\u7248\u672C\u63A7\u5236","\u654F\u6377\u5F00\u53D1","\u4EE3\u7801\u5BA1\u67E5","\u91CD\u6784","\u6587\u6863\u7F16\u5199"],
  "\u5728\u7EBF\u8003\u8BD5": ["\u9898\u5E93\u7BA1\u7406","\u81EA\u52A8\u7EC4\u5377","\u5728\u7EBF\u7B54\u9898","\u6210\u7EE9\u7EDF\u8BA1","\u9632\u4F5C\u5F0A","\u6743\u9650\u63A7\u5236","\u6570\u636E\u5B89\u5168","\u5E76\u53D1\u5904\u7406","\u5B9E\u65F6\u901A\u4FE1","\u7CFB\u7EDF\u90E8\u7F72"],
  Java: ["\u57FA\u7840\u8BED\u6CD5","\u6570\u636E\u7C7B\u578B","\u8FD0\u7B97\u7B26","\u6D41\u7A0B\u63A7\u5236","\u6570\u7EC4","\u5B57\u7B26\u4E32","\u5F02\u5E38\u5904\u7406","\u9762\u5411\u5BF9\u8C61","\u96C6\u5408\u6846\u67B6","IO\u64CD\u4F5C"],
  "Java Web": ["Servlet","JSP","Filter","Listener","JDBC","MVC\u6A21\u5F0F","\u4F1A\u8BDD\u7BA1\u7406","\u8BF7\u6C42\u8F6C\u53D1","\u8FC7\u6EE4\u5668\u94FE","\u90E8\u7F72\u914D\u7F6E"],
  "\u8BA1\u7B97\u673A\u7F51\u7EDC": ["OSI\u6A21\u578B","TCP/IP","HTTP\u534F\u8BAE","DNS\u89E3\u6790","Socket\u7F16\u7A0B","\u7F51\u7EDC\u5B89\u5168","\u8DEF\u7531\u534F\u8BAE","ARP\u534F\u8BAE","SSL/TLS","\u7F51\u7EDC\u5206\u5C42"],
};

// ─── Utility functions ────────────────────────────────────────────────────────

function escapeSQL(str) {
  return String(str).replace(/\\/g, "\\\\").replace(/'/g, "''");
}

function pickDifficulty() {
  const r = Math.random();
  if (r < 0.4) return "\u6613";
  if (r < 0.8) return "\u4E2D";
  return "\u96BE";
}

function shuffle(arr) {
  const a = [...arr];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

function toJSON(arr) {
  return JSON.stringify(arr);
}

// ─── Question title templates ─────────────────────────────────────────────────

const singleTitleTemplates = [
  "\u5173\u4E8E{kp}\u7684\u8BF4\u6CD5\uFF0C\u6B63\u786E\u7684\u662F\uFF1F",
  "{kp}\u7684\u6838\u5FC3\u4F5C\u7528\u662F\u4EC0\u4E48\uFF1F",
  "\u4EE5\u4E0B\u54EA\u9879\u4E0D\u5C5E\u4E8E{kp}\u7684\u7279\u6027\uFF1F",
  "{kp}\u9002\u5408\u7528\u5728\u4EC0\u4E48\u573A\u666F\uFF1F",
  "\u4F7F\u7528{kp}\u65F6\uFF0C\u4EE5\u4E0B\u54EA\u79CD\u505A\u6CD5\u662F\u9519\u8BEF\u7684\uFF1F",
  "\u5173\u4E8E{kp}\u7684\u63CF\u8FF0\uFF0C\u54EA\u9879\u662F\u6B63\u786E\u7684\uFF1F",
  "\u5728{subject}\u4E2D\uFF0C{kp}\u7684\u4E3B\u8981\u7528\u9014\u662F\uFF1F",
  "{kp}\u4E0E\u4EE5\u4E0B\u54EA\u4E2A\u6982\u5FF5\u5173\u7CFB\u6700\u5BC6\u5207\uFF1F",
  "\u5B66\u4E60{kp}\u65F6\u5E94\u91CD\u70B9\u638C\u63E1\u7684\u5185\u5BB9\u662F\uFF1F",
  "\u4EE5\u4E0B\u5173\u4E8E{kp}\u7684\u5E38\u89C1\u8BEF\u533A\u662F\uFF1F",
  "{kp}\u7684\u5B9E\u73B0\u539F\u7406\u662F\u4EC0\u4E48\uFF1F",
  "\u54EA\u79CD\u573A\u666F\u4E0B\u5E94\u4F18\u5148\u4F7F\u7528{kp}\uFF1F",
  "\u5173\u4E8E{kp}\u7684\u6700\u4F73\u5B9E\u8DF5\uFF0C\u4EE5\u4E0B\u54EA\u9879\u6B63\u786E\uFF1F",
  "{kp}\u89E3\u51B3\u7684\u6838\u5FC3\u95EE\u9898\u662F\u4EC0\u4E48\uFF1F",
  "\u4EE5\u4E0B\u54EA\u4E2A\u4E0D\u662F{kp}\u7684\u4F18\u70B9\uFF1F",
];

const multipleTitleTemplates = [
  "\u5173\u4E8E{kp}\u7684\u7279\u70B9\uFF0C\u4EE5\u4E0B\u54EA\u4E9B\u662F\u6B63\u786E\u7684\uFF1F\uFF08\u591A\u9009\uFF09",
  "{kp}\u7684\u4E3B\u8981\u5E94\u7528\u573A\u666F\u5305\u62EC\u54EA\u4E9B\uFF1F\uFF08\u591A\u9009\uFF09",
  "\u4F7F\u7528{kp}\u65F6\u5E94\u6CE8\u610F\u54EA\u4E9B\u4E8B\u9879\uFF1F\uFF08\u591A\u9009\uFF09",
  "\u4EE5\u4E0B\u5173\u4E8E{kp}\u7684\u63CF\u8FF0\uFF0C\u54EA\u4E9B\u662F\u6B63\u786E\u7684\uFF1F\uFF08\u591A\u9009\uFF09",
  "{kp}\u5177\u6709\u4EE5\u4E0B\u54EA\u4E9B\u4F18\u52BF\uFF1F\uFF08\u591A\u9009\uFF09",
  "\u5173\u4E8E{kp}\u7684\u5B9E\u73B0\u539F\u7406\uFF0C\u4EE5\u4E0B\u54EA\u4E9B\u8BF4\u6CD5\u6B63\u786E\uFF1F\uFF08\u591A\u9009\uFF09",
  "\u5728\u5B9E\u9645\u9879\u76EE\u4E2D\u4F7F\u7528{kp}\u7684\u597D\u5904\u6709\u54EA\u4E9B\uFF1F\uFF08\u591A\u9009\uFF09",
  "{kp}\u7684\u6838\u5FC3\u7EC4\u6210\u90E8\u5206\u5305\u62EC\u54EA\u4E9B\uFF1F\uFF08\u591A\u9009\uFF09",
];

const judgeTitleTemplates = [
  "{kp}\u53EF\u4EE5\u5B8C\u5168\u66FF\u4EE3\u5176\u4ED6\u6240\u6709\u76F8\u5173\u6280\u672F\u3002",
  "\u5728\u6240\u6709\u573A\u666F\u4E0B\uFF0C{kp}\u90FD\u662F\u6700\u4F18\u9009\u62E9\u3002",
  "{kp}\u7684\u4E3B\u8981\u76EE\u7684\u662F\u63D0\u5347\u7CFB\u7EDF\u7684\u53EF\u7EF4\u62A4\u6027\u548C\u6548\u7387\u3002",
  "\u4F7F\u7528{kp}\u65F6\u4E0D\u9700\u8981\u8003\u8651\u6027\u80FD\u5F71\u54CD\u3002",
  "{kp}\u662F{subject}\u4E2D\u7684\u91CD\u8981\u6982\u5FF5\u3002",
  "\u7406\u89E3{kp}\u5BF9\u4E8E\u638C\u63E1{subject}\u975E\u5E38\u91CD\u8981\u3002",
  "{kp}\u53EA\u80FD\u5728\u7279\u5B9A\u573A\u666F\u4E0B\u4F7F\u7528\uFF0C\u4E0D\u5177\u6709\u901A\u7528\u6027\u3002",
  "{kp}\u7684\u6838\u5FC3\u4F18\u52BF\u5728\u4E8E\u7B80\u5316\u5F00\u53D1\u6D41\u7A0B\u548C\u63D0\u9AD8\u4EE3\u7801\u8D28\u91CF\u3002",
  "\u5B66\u4E60{kp}\u65F6\u53EA\u9700\u8981\u8BB0\u4F4F\u5B9A\u4E49\u5373\u53EF\uFF0C\u65E0\u9700\u5B9E\u8DF5\u3002",
  "{kp}\u4E0E\u5B9E\u9645\u4E1A\u52A1\u5F00\u53D1\u6CA1\u6709\u5173\u7CFB\u3002",
  "{kp}\u80FD\u591F\u6709\u6548\u89E3\u51B3\u76F8\u5173\u7684\u6280\u672F\u95EE\u9898\u3002",
  "\u5728\u751F\u4EA7\u73AF\u5883\u4E2D\u4F7F\u7528{kp}\u9700\u8981\u8C28\u614E\u8BC4\u4F30\u5176\u5F71\u54CD\u3002",
];

const fillTitleTemplates = [
  "\u5728{subject}\u4E2D\uFF0C{kp}\u7684\u6838\u5FC3\u5173\u952E\u5B57\u662F____\u3002",
  "\u5B9E\u73B0{kp}\u529F\u80FD\u65F6\uFF0C\u6700\u5E38\u7528\u7684\u7C7B\u6216\u65B9\u6CD5\u662F____\u3002",
  "{kp}\u7684\u4E3B\u8981\u7279\u70B9\u53EF\u4EE5\u7528____\u6765\u6982\u62EC\u3002",
  "\u5728\u4F7F\u7528{kp}\u65F6\uFF0C\u9700\u8981\u7279\u522B\u6CE8\u610F____\u3002",
  "{kp}\u5C5E\u4E8E{subject}\u4E2D\u7684____\u7C7B\u6982\u5FF5\u3002",
  "\u914D\u7F6E{kp}\u65F6\uFF0C\u5173\u952E\u7684\u53C2\u6570\u662F____\u3002",
  "{kp}\u89E3\u51B3\u7684\u6838\u5FC3\u95EE\u9898\u662F____\u3002",
  "\u4E0E{kp}\u5BC6\u5207\u76F8\u5173\u7684\u8BBE\u8BA1\u6A21\u5F0F\u662F____\u3002",
];

const shortTitleTemplates = [
  "\u8BF7\u7B80\u8FF0{kp}\u7684\u6838\u5FC3\u539F\u7406\u548C\u5E94\u7528\u573A\u666F\u3002",
  "\u8BF7\u8BF4\u660E{kp}\u5728{subject}\u4E2D\u7684\u91CD\u8981\u6027\u53CA\u5176\u4E3B\u8981\u7279\u70B9\u3002",
  "\u8BF7\u89E3\u91CA{kp}\u7684\u5DE5\u4F5C\u539F\u7406\uFF0C\u5E76\u4E3E\u4F8B\u8BF4\u660E\u3002",
  "\u8BF7\u5206\u6790{kp}\u7684\u4F18\u7F3A\u70B9\u53CA\u9002\u7528\u573A\u666F\u3002",
  "\u8BF7\u6BD4\u8F83{kp}\u4E0E\u76F8\u5173\u6280\u672F\u7684\u5F02\u540C\u3002",
  "\u8BF7\u63CF\u8FF0{kp}\u7684\u5B9E\u73B0\u6B65\u9AA4\u548C\u6CE8\u610F\u4E8B\u9879\u3002",
  "\u8BF7\u7ED3\u5408\u5B9E\u9645\u9879\u76EE\u7ECF\u9A8C\uFF0C\u8C08\u8C08{kp}\u7684\u5E94\u7528\u3002",
  "\u8BF7\u8BF4\u660E{kp}\u5BF9\u7CFB\u7EDF\u6027\u80FD\u548C\u53EF\u7EF4\u62A4\u6027\u7684\u5F71\u54CD\u3002",
];

const codingTitleTemplates = [
  "\u8BF7\u7F16\u5199\u4EE3\u7801\u5B9E\u73B0{kp}\u7684\u57FA\u672C\u7528\u6CD5\u3002",
  "\u8BF7\u7528\u4EE3\u7801\u6F14\u793A{kp}\u5728\u5B9E\u9645\u9879\u76EE\u4E2D\u7684\u5E94\u7528\u3002",
  "\u8BF7\u7F16\u5199\u4E00\u6BB5\u4EE3\u7801\uFF0C\u5C55\u793A{kp}\u7684\u6838\u5FC3\u7528\u6CD5\u3002",
  "\u8BF7\u7528\u4EE3\u7801\u5B9E\u73B0\u4E00\u4E2A\u4F7F\u7528{kp}\u7684\u5B8C\u6574\u793A\u4F8B\u3002",
  "\u8BF7\u7F16\u5199\u4EE3\u7801\u89E3\u51B3\u4E00\u4E2A\u6D89\u53CA{kp}\u7684\u5B9E\u9645\u95EE\u9898\u3002",
  "\u8BF7\u7F16\u5199\u4EE3\u7801\uFF0C\u5C55\u793A\u5982\u4F55\u6B63\u786E\u4F7F\u7528{kp}\u3002",
];

// ─── Content generation helpers ───────────────────────────────────────────────

// Correct option descriptions for single-choice questions (varied per subject/kp)
function makeCorrectOption(subject, kp) {
  const templates = [
    `${kp}\u662F${subject}\u4E2D\u7684\u6838\u5FC3\u6982\u5FF5\uFF0C\u5728\u5B9E\u9645\u5F00\u53D1\u4E2D\u6709\u5E7F\u6CDB\u5E94\u7528`,
    `${kp}\u7684\u4E3B\u8981\u4F5C\u7528\u662F\u63D0\u5347\u4EE3\u7801\u7684\u53EF\u8BFB\u6027\u548C\u53EF\u7EF4\u62A4\u6027`,
    `${kp}\u80FD\u591F\u6709\u6548\u89E3\u51B3${subject}\u4E2D\u7684\u76F8\u5173\u6280\u672F\u95EE\u9898`,
    `${kp}\u7684\u8BBE\u8BA1\u76EE\u6807\u662F\u7B80\u5316\u5F00\u53D1\u6D41\u7A0B\u5E76\u63D0\u9AD8\u6548\u7387`,
    `${kp}\u901A\u8FC7\u5408\u7406\u7684\u62BD\u8C61\u548C\u5C01\u88C5\u5B9E\u73B0\u529F\u80FD\u9700\u6C42`,
    `${kp}\u7684\u6838\u5FC3\u4EF7\u503C\u5728\u4E8E\u63D0\u4F9B\u7EDF\u4E00\u7684\u89E3\u51B3\u65B9\u6848`,
    `${kp}\u5728${subject}\u9886\u57DF\u88AB\u5E7F\u6CDB\u91C7\u7528\uFF0C\u662F\u5FC5\u5907\u6280\u80FD`,
    `${kp}\u80FD\u663E\u8457\u63D0\u5347\u7CFB\u7EDF\u7684\u7A33\u5B9A\u6027\u548C\u53EF\u6269\u5C55\u6027`,
  ];
  return templates[Math.floor(Math.random() * templates.length)];
}

function makeWrongOption(subject, kp, variant) {
  const templates = [
    `${kp}\u53EA\u4E0E\u524D\u7AEF\u9875\u9762\u6837\u5F0F\u6709\u5173\uFF0C\u4E0E\u540E\u7AEF\u903B\u8F91\u65E0\u5173`,
    `${kp}\u4F1A\u81EA\u52A8\u7ED5\u8FC7\u6240\u6709\u5B89\u5168\u6821\u9A8C\u548C\u6743\u9650\u63A7\u5236`,
    `\u4F7F\u7528${kp}\u65F6\u65E0\u9700\u8003\u8651\u5F02\u5E38\u5904\u7406\u548C\u8FB9\u754C\u6761\u4EF6`,
    `${kp}\u5DF2\u7ECF\u88AB\u5B8C\u5168\u5E9F\u5F03\uFF0C\u73B0\u4EE3\u5F00\u53D1\u4E0D\u518D\u4F7F\u7528`,
    `${kp}\u53EA\u80FD\u7528\u4E8E\u6D4B\u8BD5\u73AF\u5883\uFF0C\u751F\u4EA7\u73AF\u5883\u7981\u6B62\u4F7F\u7528`,
    `${kp}\u7684\u6027\u80FD\u5F00\u9500\u6781\u5927\uFF0C\u4EFB\u4F55\u573A\u666F\u90FD\u4E0D\u5E94\u4F7F\u7528`,
    `${kp}\u4E0E${subject}\u6CA1\u6709\u4EFB\u4F55\u5173\u7CFB\uFF0C\u5C5E\u4E8E\u786C\u4EF6\u9886\u57DF`,
    `\u53EA\u9700\u8981\u8BB0\u4F4F${kp}\u7684\u540D\u79F0\u5373\u53EF\uFF0C\u4E0D\u5FC5\u7406\u89E3\u5176\u539F\u7406`,
    `${kp}\u53EF\u4EE5\u5B8C\u5168\u66FF\u4EE3\u6570\u636E\u5E93\u548C\u670D\u52A1\u5668`,
    `${kp}\u4E3B\u8981\u7528\u4E8E\u7ED5\u8FC7\u63A5\u53E3\u53C2\u6570\u6821\u9A8C`,
    `${kp}\u53EA\u80FD\u5728\u5355\u7EBF\u7A0B\u73AF\u5883\u4E0B\u4F7F\u7528`,
    `${kp}\u4E0D\u652F\u6301\u4EFB\u4F55\u5F62\u5F0F\u7684\u914D\u7F6E\u548C\u5B9A\u5236`,
  ];
  return templates[(variant || 0) % templates.length];
}

// Correct options for multiple-choice questions
function makeCorrectMultipleOption(subject, kp, idx) {
  const templates = [
    `${kp}\u5728${subject}\u4E2D\u6709\u5E7F\u6CDB\u7684\u5E94\u7528\u573A\u666F`,
    `${kp}\u80FD\u591F\u63D0\u5347\u4EE3\u7801\u7684\u53EF\u7EF4\u62A4\u6027`,
    `\u4F7F\u7528${kp}\u53EF\u4EE5\u63D0\u9AD8\u5F00\u53D1\u6548\u7387`,
    `${kp}\u6709\u52A9\u4E8E\u89E3\u51B3\u5B9E\u9645\u5F00\u53D1\u4E2D\u7684\u95EE\u9898`,
    `${kp}\u662F${subject}\u7684\u91CD\u8981\u7EC4\u6210\u90E8\u5206`,
    `\u7406\u89E3${kp}\u6709\u52A9\u4E8E\u638C\u63E1\u76F8\u5173\u6280\u672F`,
    `${kp}\u7684\u8BBE\u8BA1\u8003\u8651\u4E86\u6027\u80FD\u548C\u53EF\u7528\u6027`,
    `${kp}\u5728\u5DE5\u7A0B\u5B9E\u8DF5\u4E2D\u88AB\u5E7F\u6CDB\u91C7\u7528`,
  ];
  return templates[idx % templates.length];
}

function makeWrongMultipleOption(subject, kp, idx) {
  const templates = [
    `${kp}\u5DF2\u88AB\u5B8C\u5168\u5E9F\u5F03\u4E0D\u518D\u4F7F\u7528`,
    `${kp}\u53EA\u9002\u7528\u4E8E\u786C\u4EF6\u5F00\u53D1\u9886\u57DF`,
    `\u4F7F\u7528${kp}\u4F1A\u5BFC\u81F4\u6240\u6709\u5B89\u5168\u673A\u5236\u5931\u6548`,
    `${kp}\u4E0E${subject}\u65E0\u5173\uFF0C\u5C5E\u4E8E\u5176\u4ED6\u5B66\u79D1`,
    `${kp}\u5728\u4EFB\u4F55\u60C5\u51B5\u4E0B\u90FD\u4E0D\u5E94\u8BE5\u4F7F\u7528`,
    `${kp}\u65E0\u6CD5\u4E0E\u5176\u4ED6\u6280\u672F\u914D\u5408\u4F7F\u7528`,
  ];
  return templates[idx % templates.length];
}

// Fill-in-the-blank answers
function makeFillAnswer(subject, kp) {
  const answers = [
    kp,
    `${kp}\u7684\u6838\u5FC3\u539F\u7406`,
    `${kp}\u7684\u5173\u952E\u7279\u6027`,
    `${kp}\u7684\u5B9E\u73B0\u65B9\u5F0F`,
  ];
  return answers[Math.floor(Math.random() * answers.length)];
}

// Short answer content
function makeShortAnswer(subject, kp) {
  return `${kp}\u662F${subject}\u4E2D\u7684\u91CD\u8981\u77E5\u8BC6\u70B9\u3002\u5176\u6838\u5FC3\u539F\u7406\u5305\u62EC\u4EE5\u4E0B\u51E0\u4E2A\u65B9\u9762\uFF1A\u9996\u5148\uFF0C${kp}\u901A\u8FC7\u5408\u7406\u7684\u62BD\u8C61\u548C\u8BBE\u8BA1\uFF0C\u89E3\u51B3\u4E86\u5B9E\u9645\u5F00\u53D1\u4E2D\u7684\u5173\u952E\u95EE\u9898\u3002\u5176\u6B21\uFF0C${kp}\u5728\u6027\u80FD\u3001\u53EF\u7EF4\u62A4\u6027\u548C\u53EF\u6269\u5C55\u6027\u65B9\u9762\u90FD\u6709\u663E\u8457\u4F18\u52BF\u3002\u5728\u5B9E\u9645\u5E94\u7528\u4E2D\uFF0C\u5E94\u7ED3\u5408\u5177\u4F53\u4E1A\u52A1\u573A\u666F\u9009\u62E9\u5408\u9002\u7684\u4F7F\u7528\u65B9\u5F0F\uFF0C\u6CE8\u610F\u8FB9\u754C\u6761\u4EF6\u548C\u5F02\u5E38\u5904\u7406\uFF0C\u5E76\u9075\u5FAA\u76F8\u5173\u7684\u6700\u4F73\u5B9E\u8DF5\u3002\u7406\u89E3${kp}\u7684\u5E95\u5C42\u539F\u7406\u6709\u52A9\u4E8E\u5728\u590D\u6742\u573A\u666F\u4E0B\u505A\u51FA\u6B63\u786E\u7684\u6280\u672F\u51B3\u7B56\u3002`;
}

// Coding answer content
function makeCodingAnswer(subject, kp) {
  return `// ${subject} - ${kp} \u793A\u4F8B\u4EE3\u7801\n// \u4EE5\u4E0B\u4EE3\u7801\u5C55\u793A\u4E86${kp}\u7684\u57FA\u672C\u7528\u6CD5\u548C\u5E94\u7528\u573A\u666F\n\n/*\n * \u6B65\u9AA41: \u521D\u59CB\u5316\u76F8\u5173\u914D\u7F6E\u548C\u73AF\u5883\n * \u6B65\u9AA42: \u4F7F\u7528${kp}\u5B9E\u73B0\u6838\u5FC3\u903B\u8F91\n * \u6B65\u9AA43: \u6DFB\u52A0\u5F02\u5E38\u5904\u7406\u548C\u8FB9\u754C\u68C0\u67E5\n * \u6B65\u9AA44: \u6D4B\u8BD5\u9A8C\u8BC1\u529F\u80FD\u6B63\u786E\u6027\n */\n\n// \u5177\u4F53\u5B9E\u73B0\u8BF7\u53C2\u8003${subject}\u76F8\u5173\u6587\u6863\n// \u5173\u952E\u70B9: ${kp}\u7684\u6B63\u786E\u914D\u7F6E\u548C\u4F7F\u7528\u65B9\u6CD5`;
}

// ─── Main generation logic ────────────────────────────────────────────────────

function generateQuestionId(subjectCode, type, seq) {
  return `gen-${subjectCode}-${type}-${String(seq).padStart(4, "0")}`;
}

function buildInsertSQL(q) {
  const escaped = {
    id: escapeSQL(q.id),
    teacher_id: escapeSQL(q.teacher_id),
    subject: escapeSQL(q.subject),
    knowledge_point: escapeSQL(q.knowledge_point),
    difficulty: escapeSQL(q.difficulty),
    type: escapeSQL(q.type),
    title: escapeSQL(q.title),
    options_json: escapeSQL(q.options_json),
    answer_json: escapeSQL(q.answer_json),
    score: q.score,
    source_tag: escapeSQL(q.source_tag),
  };

  return `INSERT INTO question (id, teacher_id, subject, knowledge_point, difficulty, type, title, options_json, answer_json, score, source_tag) VALUES ('${escaped.id}', '${escaped.teacher_id}', '${escaped.subject}', '${escaped.knowledge_point}', '${escaped.difficulty}', '${escaped.type}', '${escaped.title}', '${escaped.options_json}', '${escaped.answer_json}', ${escaped.score}, '${escaped.source_tag}') ON DUPLICATE KEY UPDATE teacher_id=VALUES(teacher_id), subject=VALUES(subject), knowledge_point=VALUES(knowledge_point), difficulty=VALUES(difficulty), type=VALUES(type), title=VALUES(title), options_json=VALUES(options_json), answer_json=VALUES(answer_json), score=VALUES(score), source_tag=VALUES(source_tag);`;
}

function generateSingleQuestion(subject, kp, seq) {
  const sc = subjectCodes[subject];
  const titleTpl = singleTitleTemplates[Math.floor(Math.random() * singleTitleTemplates.length)];
  const title = titleTpl.replace(/\{kp\}/g, kp).replace(/\{subject\}/g, subject);

  const correct = makeCorrectOption(subject, kp);
  const wrongs = [];
  const usedVariants = new Set();
  while (wrongs.length < 3) {
    const v = Math.floor(Math.random() * 12);
    if (usedVariants.has(v)) continue;
    usedVariants.add(v);
    const w = makeWrongOption(subject, kp, v);
    if (w !== correct && !wrongs.includes(w)) wrongs.push(w);
  }

  const allOptions = shuffle([correct, ...wrongs]);
  const answer = [correct];

  return {
    id: generateQuestionId(sc, "single", seq),
    teacher_id: TEACHER_ID,
    subject,
    knowledge_point: kp,
    difficulty: pickDifficulty(),
    type: "single",
    title,
    options_json: toJSON(allOptions),
    answer_json: toJSON(answer),
    score: SCORE_MAP.single,
    source_tag: SOURCE_TAG,
  };
}

function generateMultipleQuestion(subject, kp, seq) {
  const sc = subjectCodes[subject];
  const titleTpl = multipleTitleTemplates[Math.floor(Math.random() * multipleTitleTemplates.length)];
  const title = titleTpl.replace(/\{kp\}/g, kp).replace(/\{subject\}/g, subject);

  const numCorrect = 2 + Math.floor(Math.random() * 2); // 2 or 3 correct
  const numWrong = 5 - numCorrect; // total 4 or 5 options

  const correctOptions = [];
  for (let i = 0; i < numCorrect; i++) {
    correctOptions.push(makeCorrectMultipleOption(subject, kp, i));
  }

  const wrongOptions = [];
  for (let i = 0; i < numWrong; i++) {
    wrongOptions.push(makeWrongMultipleOption(subject, kp, i));
  }

  const allOptions = shuffle([...correctOptions, ...wrongOptions]);

  return {
    id: generateQuestionId(sc, "multiple", seq),
    teacher_id: TEACHER_ID,
    subject,
    knowledge_point: kp,
    difficulty: pickDifficulty(),
    type: "multiple",
    title,
    options_json: toJSON(allOptions),
    answer_json: toJSON(correctOptions),
    score: SCORE_MAP.multiple,
    source_tag: SOURCE_TAG,
  };
}

function generateJudgeQuestion(subject, kp, seq) {
  const sc = subjectCodes[subject];
  const titleTpl = judgeTitleTemplates[Math.floor(Math.random() * judgeTitleTemplates.length)];
  const title = titleTpl.replace(/\{kp\}/g, kp).replace(/\{subject\}/g, subject);

  // Determine if the statement is correct or wrong
  const isCorrect = Math.random() > 0.4; // 60% chance correct
  let finalTitle = title;

  if (!isCorrect) {
    // Add "not" or negate the statement to make it false
    const negations = [
      `${kp}\u4E0D\u9700\u8981\u4EFB\u4F55\u914D\u7F6E\u5373\u53EF\u4F7F\u7528\u3002`,
      `${kp}\u53EF\u4EE5\u5B8C\u5168\u66FF\u4EE3${subject}\u4E2D\u7684\u6240\u6709\u5176\u4ED6\u6280\u672F\u3002`,
      `\u5728\u4EFB\u4F55\u573A\u666F\u4E0B\u90FD\u4E0D\u5E94\u4F7F\u7528${kp}\u3002`,
      `${kp}\u53EA\u80FD\u5728\u6D4B\u8BD5\u73AF\u5883\u4E2D\u4F7F\u7528\uFF0C\u751F\u4EA7\u73AF\u5883\u7981\u6B62\u3002`,
      `${kp}\u4E0E${subject}\u5B8C\u5168\u65E0\u5173\u3002`,
      `${kp}\u5DF2\u88AB\u5B8C\u5168\u5E9F\u5F03\u4E14\u4E0D\u518D\u63A8\u8350\u4F7F\u7528\u3002`,
    ];
    finalTitle = negations[Math.floor(Math.random() * negations.length)];
  }

  const answer = isCorrect ? "\u6B63\u786E" : "\u9519\u8BEF";

  return {
    id: generateQuestionId(sc, "judge", seq),
    teacher_id: TEACHER_ID,
    subject,
    knowledge_point: kp,
    difficulty: pickDifficulty(),
    type: "judge",
    title: finalTitle,
    options_json: toJSON(["\u6B63\u786E", "\u9519\u8BEF"]),
    answer_json: toJSON([answer]),
    score: SCORE_MAP.judge,
    source_tag: SOURCE_TAG,
  };
}

function generateFillQuestion(subject, kp, seq) {
  const sc = subjectCodes[subject];
  const titleTpl = fillTitleTemplates[Math.floor(Math.random() * fillTitleTemplates.length)];
  const title = titleTpl.replace(/\{kp\}/g, kp).replace(/\{subject\}/g, subject);
  const answer = makeFillAnswer(subject, kp);

  return {
    id: generateQuestionId(sc, "fill", seq),
    teacher_id: TEACHER_ID,
    subject,
    knowledge_point: kp,
    difficulty: pickDifficulty(),
    type: "fill",
    title,
    options_json: toJSON([]),
    answer_json: toJSON([answer]),
    score: SCORE_MAP.fill,
    source_tag: SOURCE_TAG,
  };
}

function generateShortQuestion(subject, kp, seq) {
  const sc = subjectCodes[subject];
  const titleTpl = shortTitleTemplates[Math.floor(Math.random() * shortTitleTemplates.length)];
  const title = titleTpl.replace(/\{kp\}/g, kp).replace(/\{subject\}/g, subject);
  const answer = makeShortAnswer(subject, kp);

  return {
    id: generateQuestionId(sc, "short", seq),
    teacher_id: TEACHER_ID,
    subject,
    knowledge_point: kp,
    difficulty: pickDifficulty(),
    type: "short",
    title,
    options_json: toJSON([]),
    answer_json: toJSON([answer]),
    score: SCORE_MAP.short,
    source_tag: SOURCE_TAG,
  };
}

function generateCodingQuestion(subject, kp, seq) {
  const sc = subjectCodes[subject];
  const titleTpl = codingTitleTemplates[Math.floor(Math.random() * codingTitleTemplates.length)];
  const title = titleTpl.replace(/\{kp\}/g, kp).replace(/\{subject\}/g, subject);
  const answer = makeCodingAnswer(subject, kp);

  return {
    id: generateQuestionId(sc, "coding", seq),
    teacher_id: TEACHER_ID,
    subject,
    knowledge_point: kp,
    difficulty: pickDifficulty(),
    type: "coding",
    title,
    options_json: toJSON([]),
    answer_json: toJSON([answer]),
    score: SCORE_MAP.coding,
    source_tag: SOURCE_TAG,
  };
}

// ─── Main ─────────────────────────────────────────────────────────────────────

function main() {
  console.log("=== Question Generation Script ===\n");

  const allQuestions = [];
  const stats = {};
  const types = ["single", "multiple", "judge", "fill", "short", "coding"];
  const generators = {
    single: generateSingleQuestion,
    multiple: generateMultipleQuestion,
    judge: generateJudgeQuestion,
    fill: generateFillQuestion,
    short: generateShortQuestion,
    coding: generateCodingQuestion,
  };

  for (const subject of Object.keys(knowledgePoints)) {
    const kps = knowledgePoints[subject];
    const current = currentState[subject] || { single: 0, multiple: 0, judge: 0, fill: 0, short: 0, coding: 0 };

    stats[subject] = {};

    for (const type of types) {
      const have = current[type] || 0;
      const needed = Math.max(0, MIN_PER_TYPE - have);

      if (needed === 0) {
        stats[subject][type] = { existing: have, generated: 0, total: have };
        continue;
      }

      // Distribute questions across knowledge points
      let generated = 0;
      let seq = 1;

      while (generated < needed) {
        const kpIndex = generated % kps.length;
        const kp = kps[kpIndex];
        const q = generators[type](subject, kp, seq);
        allQuestions.push(q);
        generated++;
        seq++;
      }

      stats[subject][type] = { existing: have, generated, total: have + generated };
    }
  }

  // ─── Write SQL file ───────────────────────────────────────────────────────

  const lines = [];
  lines.push("-- ============================================================");
  lines.push("-- Bulk Question Import");
  lines.push("-- Generated: " + new Date().toISOString());
  lines.push("-- Source Tag: " + SOURCE_TAG);
  lines.push("-- Total Questions: " + allQuestions.length);
  lines.push("-- ============================================================\n");
  lines.push("SET NAMES utf8mb4;\n");

  for (const q of allQuestions) {
    lines.push(buildInsertSQL(q));
  }

  const sql = lines.join("\n");
  fs.writeFileSync(OUTPUT_FILE, sql, "utf8");

  // ─── Print statistics ─────────────────────────────────────────────────────

  console.log(`Output: ${OUTPUT_FILE}`);
  console.log(`Total questions generated: ${allQuestions.length}\n`);

  // Per-subject stats
  console.log("=== Per-Subject Statistics ===");
  console.log("Subject".padEnd(16) + "Type".padEnd(12) + "Existing".padEnd(10) + "Generated".padEnd(10) + "Total");
  console.log("-".repeat(58));

  let totalGenerated = 0;
  const typeGeneratedTotals = { single: 0, multiple: 0, judge: 0, fill: 0, short: 0, coding: 0 };

  for (const subject of Object.keys(stats)) {
    for (const type of types) {
      const s = stats[subject][type];
      totalGenerated += s.generated;
      typeGeneratedTotals[type] += s.generated;
      if (s.generated > 0) {
        console.log(
          subject.padEnd(16) + type.padEnd(12) +
          String(s.existing).padEnd(10) +
          String(s.generated).padEnd(10) +
          String(s.total)
        );
      }
    }
  }

  console.log("-".repeat(58));
  console.log(`\nTotal generated: ${totalGenerated}`);

  console.log("\n=== Per-Type Totals ===");
  for (const type of types) {
    console.log(`  ${type.padEnd(12)}: ${typeGeneratedTotals[type]}`);
  }

  console.log(`\nDone! SQL file written to: ${OUTPUT_FILE}`);
}

main();
