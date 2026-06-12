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

  // Find all subjects and their raw byte representations
  const subjects = [...new Set(questions.map(q => q.subject))];
  console.log("All subjects:");
  for (const s of subjects) {
    const count = questions.filter(q => q.subject === s).length;
    const hex = Buffer.from(s).toString('hex');
    console.log(`  "${s}" (${count} questions) hex: ${hex}`);
  }

  // Find the broken ones
  const broken = subjects.filter(s => s.includes('\ufffd') || s.includes('?'));
  console.log("\nBroken subjects:", broken);
})();
