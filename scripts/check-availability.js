const http = require("http");
const data = JSON.stringify({ username: "teacher", password: "123456" });

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
  const loginRes = await apiCall("POST", "/api/login", data);
  const userId = loginRes.user.id;
  console.log("Logged in as:", loginRes.user.name, "(" + userId + ")");

  const bs = await apiCall("GET", "/api/bootstrap", null, { "X-User-Id": userId });
  const questions = bs.questions || [];
  const papers = bs.papers || [];

  console.log("\nTotal questions:", questions.length);
  console.log("Total papers:", papers.length);

  // Count reserved question IDs
  const reservedIds = new Set();
  for (const paper of papers) {
    for (const qId of (paper.questionIds || [])) {
      reservedIds.add(qId);
    }
  }
  console.log("Reserved question IDs (used in papers):", reservedIds.size);

  // Count available questions
  const available = questions.filter(q => !reservedIds.has(q.id));
  console.log("Available questions (not in papers):", available.length);

  // Show available by subject and type
  const bySubjType = {};
  for (const q of available) {
    const key = q.subject + " / " + q.type;
    bySubjType[key] = (bySubjType[key] || 0) + 1;
  }
  console.log("\nAvailable by subject+type:");
  for (const [k, v] of Object.entries(bySubjType).sort()) {
    console.log("  " + k + ": " + v);
  }

  // Show paper details
  console.log("\nPapers:");
  for (const p of papers) {
    console.log("  " + p.name + " - " + (p.questionIds || []).length + " questions");
  }
})();
