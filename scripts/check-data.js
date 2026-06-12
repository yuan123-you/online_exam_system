const http = require("http");
const data = JSON.stringify({ username: "teacher", password: "123456" });
const req = http.request({ hostname: "localhost", port: 8080, path: "/api/login", method: "POST", headers: { "Content-Type": "application/json", "Content-Length": Buffer.byteLength(data) } }, (res) => {
  let body = "";
  res.on("data", (chunk) => body += chunk);
  res.on("end", () => {
    const json = JSON.parse(body);
    console.log("Login:", JSON.stringify(json));
    if (json.user) {
      const userId = json.user.id;
      const req2 = http.request({ hostname: "localhost", port: 8080, path: "/api/bootstrap", method: "GET", headers: { "X-User-Id": userId } }, (res2) => {
        let body2 = "";
        res2.on("data", (chunk) => body2 += chunk);
        res2.on("end", () => {
          const bs = JSON.parse(body2);
          const questions = bs.questions || [];
          console.log("Total questions:", questions.length);
          const subjects = [...new Set(questions.map(q => q.subject))];
          console.log("Subjects:", subjects);
          const types = [...new Set(questions.map(q => q.type))];
          console.log("Types:", types);
          const bySubjType = {};
          for (const q of questions) {
            const key = q.subject + " / " + q.type;
            bySubjType[key] = (bySubjType[key] || 0) + 1;
          }
          console.log("By subject+type:");
          for (const [k, v] of Object.entries(bySubjType).sort()) {
            console.log("  " + k + ": " + v);
          }
        });
      });
      req2.end();
    }
  });
});
req.write(data);
req.end();
