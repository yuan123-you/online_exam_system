const http = require("http");

function apiCall(method, path, body, headers = {}) {
  return new Promise((resolve, reject) => {
    const opts = { hostname: "localhost", port: 8080, path, method, headers: { "Content-Type": "application/json", ...headers } };
    if (body) opts.headers["Content-Length"] = Buffer.byteLength(body);
    const req = http.request(opts, (res) => {
      let chunks = [];
      res.on("data", (c) => chunks.push(c));
      res.on("end", () => {
        const buf = Buffer.concat(chunks);
        resolve(JSON.parse(buf.toString("utf8")));
      });
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

  // Find ALL records with replacement characters
  const broken = questions.filter(q => q.subject && q.subject.includes("\ufffd"));
  console.log(`Found ${broken.length} broken records:`);
  for (const q of broken) {
    console.log(`  ID: ${q.id}, subject: "${q.subject}", hex: ${Buffer.from(q.subject).toString("hex")}`);
  }

  // Output the IDs for deletion
  if (broken.length > 0) {
    const ids = broken.map(q => `'${q.id}'`).join(",");
    console.log(`\nDELETE SQL: DELETE FROM question WHERE id IN (${ids});`);
  }
})();
