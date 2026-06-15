/**
 * 构建一致性验证脚本
 * 用于检查 dist/ 与 backend/src/main/resources/static/ 的一致性，
 * 以及构建产物是否完整可用。
 *
 * 用法: node scripts/verify-build-consistency.js
 */
const fs = require("fs");
const path = require("path");

const ROOT = path.join(__dirname, "..");
const DIST_DIR = path.join(ROOT, "dist");
const STATIC_DIR = path.join(ROOT, "backend", "src", "main", "resources", "static");

let errors = 0;
let warnings = 0;

function error(msg) {
  console.error(`[ERROR] ${msg}`);
  errors++;
}

function warn(msg) {
  console.warn(`[WARN] ${msg}`);
  warnings++;
}

function ok(msg) {
  console.log(`[OK] ${msg}`);
}

// 1. 检查 dist/ 目录是否存在
if (!fs.existsSync(DIST_DIR)) {
  error("dist/ 目录不存在，请先运行 npm run build:web");
} else {
  ok("dist/ 目录存在");
}

// 2. 检查 backend static 目录是否存在
if (!fs.existsSync(STATIC_DIR)) {
  error("backend/src/main/resources/static/ 目录不存在");
} else {
  ok("backend/src/main/resources/static/ 目录存在");
}

// 3. 检查 dist/index.html 是否存在
const distIndex = path.join(DIST_DIR, "index.html");
if (!fs.existsSync(distIndex)) {
  error("dist/index.html 不存在");
} else {
  ok("dist/index.html 存在");

  // 检查 index.html 中引用的资源文件是否存在
  const html = fs.readFileSync(distIndex, "utf8");
  const jsMatch = html.match(/src="([^"]+\.js)"/);
  const cssMatch = html.match(/href="([^"]+\.css)"/);
  const preloadMatch = html.match(/href="([^"]+\.js)"/g);

  if (jsMatch) {
    const jsFile = path.join(DIST_DIR, jsMatch[1]);
    if (!fs.existsSync(jsFile)) {
      error(`index.html 引用的 JS 文件不存在: ${jsMatch[1]}`);
    } else {
      ok(`入口 JS 文件存在: ${jsMatch[1]}`);
    }
  } else {
    error("index.html 中未找到 JS 引用");
  }

  if (cssMatch) {
    const cssFile = path.join(DIST_DIR, cssMatch[1]);
    if (!fs.existsSync(cssFile)) {
      error(`index.html 引用的 CSS 文件不存在: ${cssMatch[1]}`);
    } else {
      ok(`入口 CSS 文件存在: ${cssMatch[1]}`);
    }
  } else {
    warn("index.html 中未找到 CSS 引用（可能使用 JS 内联样式）");
  }
}

// 4. 比对 dist/ 和 backend static/ 的文件一致性
function listFiles(dir, base = "") {
  if (!fs.existsSync(dir)) return [];
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const rel = base ? `${base}/${entry.name}` : entry.name;
    if (entry.isDirectory()) {
      files.push(...listFiles(path.join(dir, entry.name), rel));
    } else {
      files.push(rel);
    }
  }
  return files;
}

if (fs.existsSync(DIST_DIR) && fs.existsSync(STATIC_DIR)) {
  const distFiles = new Set(listFiles(DIST_DIR));
  const staticFiles = new Set(listFiles(STATIC_DIR));

  const onlyInDist = [...distFiles].filter((f) => !staticFiles.has(f));
  const onlyInStatic = [...staticFiles].filter((f) => !distFiles.has(f));

  if (onlyInDist.length > 0) {
    warn(`dist/ 中有 ${onlyInDist.length} 个文件未同步到 backend static/: ${onlyInDist.slice(0, 5).join(", ")}${onlyInDist.length > 5 ? " ..." : ""}`);
  }

  if (onlyInStatic.length > 0) {
    warn(`backend static/ 中有 ${onlyInStatic.length} 个文件不在 dist/ 中（可能是旧构建残留）: ${onlyInStatic.slice(0, 5).join(", ")}${onlyInStatic.length > 5 ? " ..." : ""}`);
  }

  if (onlyInDist.length === 0 && onlyInStatic.length === 0) {
    ok("dist/ 与 backend/src/main/resources/static/ 文件完全一致");
  } else {
    // 检查核心文件是否一致
    const coreFiles = ["index.html"];
    for (const f of coreFiles) {
      const distContent = fs.readFileSync(path.join(DIST_DIR, f), "utf8");
      const staticContent = fs.readFileSync(path.join(STATIC_DIR, f), "utf8");
      if (distContent === staticContent) {
        ok(`核心文件 ${f} 内容一致`);
      } else {
        error(`核心文件 ${f} 内容不一致`);
      }
    }
  }
}

// 5. 检查构建产物中是否包含旧版本的残留文件
if (fs.existsSync(STATIC_DIR)) {
  const assetsDir = path.join(STATIC_DIR, "assets");
  if (fs.existsSync(assetsDir)) {
    const jsFiles = fs.readdirSync(assetsDir).filter((f) => f.endsWith(".js"));
    const cssFiles = fs.readdirSync(assetsDir).filter((f) => f.endsWith(".css"));

    // 检查是否有多个版本的入口文件（说明有旧版本残留）
    const indexJsFiles = jsFiles.filter((f) => f.startsWith("index-"));
    const appJsFiles = jsFiles.filter((f) => f.startsWith("app-"));
    const routerJsFiles = jsFiles.filter((f) => f.startsWith("router-"));

    if (indexJsFiles.length > 1) warn(`发现多个入口 JS 文件: ${indexJsFiles.join(", ")}`);
    if (appJsFiles.length > 2) warn(`发现多个应用 JS 文件: ${appJsFiles.join(", ")}`);
    if (routerJsFiles.length > 1) warn(`发现多个路由 JS 文件: ${routerJsFiles.join(", ")}`);

    if (indexJsFiles.length === 1) ok(`唯一入口 JS: ${indexJsFiles[0]}`);
    if (appJsFiles.length <= 2) ok(`应用 JS 文件数量正常: ${appJsFiles.join(", ")}`);
    if (routerJsFiles.length === 1) ok(`唯一路由 JS: ${routerJsFiles[0]}`);
  }
}

// 6. 检查 vite.config.ts 中的 outDir 配置
const viteConfig = fs.readFileSync(path.join(ROOT, "vite.config.ts"), "utf8");
if (viteConfig.includes('outDir: "dist"') || viteConfig.includes("outDir: 'dist'")) {
  ok("vite.config.ts outDir 配置为 dist/");
} else if (viteConfig.includes("backend/src/main/resources/static")) {
  error("vite.config.ts outDir 仍指向 backend/src/main/resources/static，应改为 dist/");
} else {
  warn(`vite.config.ts outDir 配置异常，请检查`);
}

// 输出结果
console.log("\n" + "=".repeat(50));
if (errors === 0 && warnings === 0) {
  console.log("所有检查通过！构建一致性验证成功。");
} else {
  console.log(`验证完成: ${errors} 个错误, ${warnings} 个警告`);
  if (errors > 0) {
    process.exit(1);
  }
}
