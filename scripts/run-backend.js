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

// 读取 .env 文件并注入环境变量
function loadEnv() {
  const envPath = path.join(root, ".env");
  const env = { ...process.env };
  if (fs.existsSync(envPath)) {
    const content = fs.readFileSync(envPath, "utf-8");
    for (const line of content.split("\n")) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith("#")) continue;
      const eqIndex = trimmed.indexOf("=");
      if (eqIndex > 0) {
        const key = trimmed.substring(0, eqIndex).trim();
        const value = trimmed.substring(eqIndex + 1).trim();
        if (key && !(key in env)) {
          env[key] = value;
        }
      }
    }
    console.log("[env] Loaded .env file");
  } else {
    console.warn("[env] .env file not found, using system environment variables only");
  }
  return env;
}

const depClasspath = fs.readFileSync(cpFile, "utf-8").trim();
const sep = process.platform === "win32" ? ";" : ":";
const classpath = `${classesDir}${sep}${depClasspath}`;

const child = spawn("java", ["-cp", classpath, "com.onlineexam.OnlineExamApplication"], {
  cwd: root,
  stdio: "inherit",
  env: loadEnv(),
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
