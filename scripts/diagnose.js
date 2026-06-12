const http = require("http");

function apiCall(method, path, body, headers = {}) {
  return new Promise((resolve, reject) => {
    const opts = { hostname: "localhost", port: 8080, path, method, headers: { "Content-Type": "application/json", ...headers } };
    if (body) opts.headers["Content-Length"] = Buffer.byteLength(body);
    const req = http.request(opts, (res) => {
      let chunks = "";
      res.on("data", (c) => chunks += c);
      res.on("end", () => { try { resolve(JSON.parse(chunks)); } catch(e) { resolve(chunks); } });
    });
    req.on("error", reject);
    if (body) req.write(body);
    req.end();
  });
}

(async () => {
  const loginRes = await apiCall("POST", "/api/login", JSON.stringify({ username: "teacher", password: "123456" }));
  const userId = loginRes.user.id;
  const bs = await apiCall("GET", "/api/bootstrap", null, { "X-User-Id": userId });
  const questions = bs.questions || [];

  // Find subjects containing replacement characters
  const subjects = [...new Set(questions.map(q => q.subject))];
  const broken = subjects.filter(s => s.includes("\ufffd"));
  console.log("Broken subjects:", broken.map(s => `"${s}"`));

  for (const subj of broken) {
    const qs = questions.filter(q => q.subject === subj);
    console.log(`\nSubject "${subj}" has ${qs.length} questions:`);
    for (const q of qs) {
      console.log(`  ID: ${q.id}, type: ${q.type}, hex: ${Buffer.from(q.subject).toString("hex")}`);
    }
  }

  // Also check: how many subjects and questions total
  const reservedIds = new Set();
  for (const paper of (bs.papers || [])) {
    for (const qId of (paper.questionIds || [])) reservedIds.add(qId);
  }
  const available = questions.filter(q => !reservedIds.has(q.id));
  console.log(`\n=== TOTALS ===`);
  console.log(`Total questions: ${questions.length}`);
  console.log(`Available (not in papers): ${available.length}`);
  console.log(`Subjects: ${subjects.filter(s => !s.includes("\ufffd")).length} clean + ${broken.length} broken`);

  // Per-subject availability
  const cleanSubjects = subjects.filter(s => !s.includes("\ufffd")).sort();
  console.log(`\n=== PER SUBJECT AVAILABILITY ===`);
  const types = ["single", "multiple", "judge", "fill", "short", "coding"];
  let allPass = true;
  for (const subj of cleanSubjects) {
    const pool = available.filter(q => q.subject === subj);
    const total = pool.length;
    const counts = {};
    for (const t of types) counts[t] = pool.filter(q => q.type === t).length;
    const minCount = Math.min(...types.map(t => counts[t]));
    const status = minCount >= 5 ? "OK" : "LOW";
    if (status === "LOW") allPass = false;
    console.log(`  ${subj}: ${total} total | single:${counts.single} multi:${counts.multiple} judge:${counts.judge} fill:${counts.fill} short:${counts.short} coding:${counts.coding} [${status}]`);
  }
  console.log(`\nAll subjects have >=5 of each type: ${allPass}`);
})();
