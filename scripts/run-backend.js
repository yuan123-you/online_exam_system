const { spawn } = require("child_process");
const fs = require("fs");
const path = require("path");

const root = path.resolve(__dirname, "..");
const cpFile = path.join(root, "backend", "target", "cp.txt");
const classesDir = path.join(root, "backend", "target", "classes");

if (!fs.existsSync(cpFile)) {
  console.error("Error: cp.txt not found. Run 'npm run backend:build' first.");
  process.exit(1);
}

const depClasspath = fs.readFileSync(cpFile, "utf-8").trim();
const sep = process.platform === "win32" ? ";" : ":";
const classpath = `${classesDir}${sep}${depClasspath}`;

const child = spawn("java", ["-cp", classpath, "com.onlineexam.OnlineExamApplication"], {
  cwd: root,
  stdio: "inherit",
  env: { ...process.env },
});

child.on("error", (err) => {
  console.error("Failed to start Java process:", err.message);
  process.exit(1);
});

child.on("exit", (code) => {
  process.exit(code ?? 0);
});

process.on("SIGINT", () => {
  child.kill("SIGINT");
});

process.on("SIGTERM", () => {
  child.kill("SIGTERM");
});
