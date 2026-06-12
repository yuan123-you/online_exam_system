const http = require("http");

function apiCall(method, path, body, headers = {}) {
  return new Promise((resolve, reject) => {
    const opts = { hostname: "localhost", port: 8080, path, method, headers: { "Content-Type": "application/json", ...headers } };
    if (body) opts.headers["Content-Length"] = Buffer.byteLength(body);
    const req = http.request(opts, (res) => {
      let chunks = "";
      res.on("data", (c) => chunks += c);
      res.on("end", () => resolve(JSON.parse(chunks)));
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
  const papers = bs.papers || [];

  // Simulate getSelectablePaperQuestions
  const reservedIds = new Set();
  for (const paper of papers) {
    for (const qId of (paper.questionIds || [])) {
      reservedIds.add(qId);
    }
  }
  const available = questions.filter(q => !reservedIds.has(q.id));

  console.log("=== Auto-Generate Simulation ===\n");
  console.log("Total available questions:", available.length);
  console.log("Total subjects:", [...new Set(available.map(q => q.subject))].length);

  // Test: simulate auto-generate for "Spring" with default counts
  const testSubjects = ["Spring", "Java 基础", "MySQL", "Vue 3", "数据库"];
  const typeMap = { single: 5, multiple: 5, judge: 5, fill: 5, short: 2, coding: 1 };
  
  for (const subject of testSubjects) {
    console.log(`\n--- Subject: ${subject} ---`);
    const pool = available.filter(q => q.subject === subject);
    let totalScore = 0;
    let totalQs = 0;
    let allOk = true;
    
    for (const [type, count] of Object.entries(typeMap)) {
      const candidates = pool.filter(q => q.type === type);
      const score = type === "single" ? 4 : type === "multiple" ? 5 : type === "judge" ? 3 : type === "fill" ? 4 : type === "short" ? 10 : 15;
      if (candidates.length >= count) {
        totalScore += count * score;
        totalQs += count;
        console.log(`  ${type}: ${candidates.length} available, need ${count} -> OK (${count * score} pts)`);
      } else {
        console.log(`  ${type}: ${candidates.length} available, need ${count} -> FAIL`);
        allOk = false;
      }
    }
    console.log(`  Total: ${totalQs} questions, ${totalScore} points (need 100)`);
    console.log(`  Result: ${allOk ? "PASS" : "FAIL"}`);
  }

  // Check ALL subjects
  console.log("\n\n=== ALL Subjects Check ===");
  const allSubjects = [...new Set(available.map(q => q.subject))].sort();
  let passCount = 0;
  let failCount = 0;
  for (const subject of allSubjects) {
    const pool = available.filter(q => q.subject === subject);
    let allOk = true;
    for (const [type, count] of Object.entries(typeMap)) {
      const candidates = pool.filter(q => q.type === type);
      if (candidates.length < count) allOk = false;
    }
    if (allOk) passCount++;
    else {
      failCount++;
      console.log(`  FAIL: ${subject}`);
      for (const [type, count] of Object.entries(typeMap)) {
        const cands = pool.filter(q => q.type === type).length;
        if (cands < count) console.log(`    ${type}: ${cands}/${count}`);
      }
    }
  }
  console.log(`\n  PASS: ${passCount}/${allSubjects.length} subjects`);
  console.log(`  FAIL: ${failCount}/${allSubjects.length} subjects`);
})();
