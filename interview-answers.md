# 前端面试题参考答案

---

## 1. Vue 3 Composition API 相比 Options API 解决了什么问题？

Options API 按 data、methods、computed、watch 等选项组织代码。当一个功能涉及多种选项时，相关逻辑被拆散到不同位置。比如"题库筛选"功能，状态在 data 里，过滤逻辑在 methods 里，派生数据在 computed 里，联动监听在 watch 里，阅读代码时需要反复跳转。

Composition API 按功能组织代码。同一个功能的响应式状态、计算属性、操作方法写在同一个 setup 函数或 composable 中，逻辑内聚。

另一个核心优势是逻辑复用。Options API 用 mixins 复用逻辑，但 mixins 存在命名冲突、来源不明、类型推断差等问题。Composition API 通过 composable 函数复用，本质就是一个普通函数，输入输出清晰，TypeScript 支持也更好。

我在项目中写了 4 个 composable（useDeviceType、useResponsiveLayout、useSmoothScroll、useAutoScroll），比如 useDeviceType 封装了窗口监听和断点判断，任何组件 import 调用即可获得 isMobile、isTablet、isDesktop 响应式变量，不需要重复写监听逻辑。

---

## 2. ref 和 reactive 的区别？什么时候用哪个？

ref 用于包装任意类型的值（基本类型或对象），访问时需要 .value。reactive 用于包装对象类型（Object、Array、Map、Set），访问时不需要 .value，直接读写属性。

底层实现上，ref 对于对象类型内部也调用了 reactive，但额外包了一层 { value: ... } 结构。reactive 基于 Proxy 实现深度响应式。

选择原则：基本类型（string、number、boolean）必须用 ref，因为 Proxy 只能代理对象。对象类型两者都可以，但如果需要整体替换对象引用（比如 `state = newState`），用 ref 更安全，因为 reactive 的引用替换会丢失响应性。

一个常见的坑是 reactive 解构：`const { name } = reactiveObj` 解构出来的 name 不是响应式的，需要用 `toRefs(reactiveObj)` 或 `toRef(reactiveObj, 'name')` 处理。

---

## 3. computed 和 watch 的区别？项目中哪些地方用到了 computed？

computed 是计算属性，有缓存机制——只有依赖的响应式数据变化时才会重新计算，多次访问同一个 computed 只算一次。它必须有返回值，适合同步派生数据。

watch 是侦听器，没有缓存，适合执行副作用（异步请求、操作 DOM、写日志等）。它不需要返回值，可以监听多个数据源，可以配置 immediate（立即执行一次）和 deep（深度监听）。

项目中 computed 使用非常多，举几个例子：
- 路由守卫中通过 `store.isAdmin`、`store.role` 等 computed 判断当前用户角色，决定可访问的路由
- Pinia Store 中 `menuItems` 是 computed，根据当前角色动态返回菜单列表
- ExamSessionModal 中 `currentQuestion` 是 `computed(() => exam.questions[currentIndex.value])`，切换题目索引时自动返回对应题目
- `countdown` 是 computed，根据 deadlineAt 和当前时间实时计算剩余时间
- `progressPercent` 是 computed，根据已答题数 / 总题数算出百分比

---

## 4. Vue 3 的响应式原理？和 Vue 2 有什么区别？

Vue 2 使用 Object.defineProperty 对每个属性做 getter/setter 劫持。问题是：无法检测属性的新增和删除（需要 Vue.set / Vue.delete），无法检测数组通过索引修改元素（需要 splice 或 Vue.set），初始化时需要递归遍历对象所有属性做转换，如果对象层级深会有性能开销。

Vue 3 使用 Proxy 对整个对象做代理。Proxy 可以拦截 get、set、has、deleteProperty、ownKeys 等十几种操作，天然支持属性新增/删除的响应式，支持数组索引修改和 length 变化的响应式。并且 Proxy 是惰性代理，只有在访问到嵌套对象时才会创建下一层的 Proxy，不需要初始化时递归转换，性能更好。

Vue 3 还引入了 ref 和 effect 系统。ref 用于包装基本类型（Proxy 无法代理基本类型），effect 是响应式的底层调度器，computed、watch 都是基于 effect 实现的。

---

## 5. 为什么用 Pinia 而不是 Vuex？Pinia 的核心概念？

Pinia 是 Vue 官方推荐的状态管理库，相比 Vuex 4 有几个优势：

去掉了 mutations。Vuex 中修改状态必须先 mutation 再 commit，流程繁琐。Pinia 直接在 actions 中修改 state，代码更简洁。

TypeScript 支持更好。Pinia 的 API 设计天然支持类型推断，不需要额外的类型声明文件，store 的 state、getters、actions 类型自动推导。

支持多 store 实例。Vuex 是单一 store 嵌套 modules，Pinia 鼓励创建多个扁平的 store，各 store 之间互相独立，也可以互相引用。

API 更简洁。Pinia 核心概念只有三个：state（响应式状态）、getters（计算属性）、actions（同步/异步操作）。不需要记 mutations、modules、namespaced 等概念。

我项目中只有一个 store（useAppStore），因为业务逻辑集中在一个应用中。这个 store 有 67KB，包含所有认证、CRUD、AI 功能、考试流程等逻辑。如果项目继续增长，可以按业务域拆分为 authStore、examStore、aiStore 等。

---

## 6. Vue Router 的路由守卫有哪些？你怎么实现权限控制的？

路由守卫分三种层级：

全局守卫：router.beforeEach（导航前）、router.afterEach（导航后）。在每个路由切换时执行。

路由独享守卫：在路由配置中写 beforeEnter，只对该路由生效。

组件内守卫：beforeRouteEnter、beforeRouteUpdate、beforeRouteLeave，写在组件内部。

我的权限控制实现在 router.beforeEach 中，流程是：

第一步，检查 authReady。如果认证初始化还没完成（main.ts 中 await initAuth() 还没执行完），直接 next() 放行，不做任何拦截，避免白屏。

第二步，检查公开页面。如果路由 meta 中标记了 public（如 /login），直接放行。如果已登录用户访问登录页，重定向到首页。

第三步，检查认证状态。如果路由匹配了 requiresAuth，且 bootstrap 为 null（未登录），重定向到 /login。

第四步，检查角色权限。从路由 meta.roles 获取允许的角色列表，与当前用户角色对比。如果不匹配，重定向到该用户的第一个可用菜单页。

关键代码逻辑就是：`requiredRoles && !requiredRoles.includes(store.role)` 时做拦截。

---

## 7. interface 和 type 有什么区别？项目中怎么组织的？

interface 声明对象形状，支持声明合并（同名 interface 自动合并）、extends 继承。type 是类型别名，更灵活，可以表示联合类型（`'a' | 'b'`）、交叉类型（A & B）、元组、映射类型等。

实际选择：描述对象形状优先用 interface，需要联合类型或复杂类型运算用 type。

我项目在 src/types.ts 中集中定义了所有业务数据模型，全部使用 interface：User、Question、Paper、Exam、SubmissionReview、WrongBookEntry、BootstrapData 等 20+ 个接口。这样 API 请求的返回类型、组件 props 类型、Store 状态类型都引用同一套定义，保证类型一致。

另外还定义了一些 type：`type Role = 'admin' | 'teacher' | 'student'`，`type QuestionType = 'single' | 'multiple' | 'judge' | 'fill' | 'short' | 'coding'`。这些是有限枚举值，用联合类型更合适。

---

## 8. 什么是泛型？项目中的使用场景？

泛型是类型参数化。普通函数接收值参数，泛型函数额外接收类型参数，在调用时确定具体类型。

项目中典型的泛型场景是 API 客户端的 request 函数：

```typescript
async function request<T>(url: string, options: RequestInit = {}): Promise<T> {
  // ... 发请求、解析 JSON、错误处理
  return payload as T;
}
```

调用时指定 T 的具体类型：

```typescript
const data = await request<BootstrapData>("/api/bootstrap");
// data 的类型是 BootstrapData，有完整的类型提示和检查

const result = await request<{ user: User }>("/api/login", { ... });
// result.user 有 User 接口的类型推断
```

好处是每个 API 函数的返回类型都是精确的，调用方获得完整的 TypeScript 智能提示，避免 any 类型导致的运行时错误。

Pinia store 的 defineStore 也是泛型，它根据 setup 函数的返回值自动推断 state、getters、actions 的类型。

---

## 9. as const、keyof、typeof 的使用场景？

as const 将对象或数组变为只读字面量类型。比如：

```typescript
const colors = ['red', 'blue', 'green'] as const;
// 类型是 readonly ['red', 'blue', 'green']，而不是 string[]
```

适合定义常量配置，防止意外修改，同时让类型更精确。

keyof 获取对象类型的所有键名联合类型：

```typescript
interface User { id: string; name: string; role: Role; }
type UserKey = keyof User; // 'id' | 'name' | 'role'
```

适合约束函数参数只能是某个对象的合法属性名，比如 `function sortBy<K extends keyof User>(key: K)`。

typeof 从值反推类型：

```typescript
const config = { port: 8080, host: 'localhost' };
type Config = typeof config; // { port: number; host: string; }
```

适合从现有 JS 对象中提取类型，而不需要手写 interface。

---

## 10. authReady 没初始化完时导航怎么处理？为什么？

直接 next() 放行，不做任何拦截。

原因是应用启动时，main.ts 中会先 await initAuth() 完成认证初始化（从 localStorage 恢复登录状态、请求 bootstrap 数据等），然后才挂载 router。但在某些情况下（比如 Vite HMR 或极端时序），beforeEach 可能在 initAuth 完成前被触发。

如果此时拦截并跳转到 /login，会导致已登录用户被误踢到登录页，因为 bootstrap 还是 null，但用户实际上已经登录了。

所以用 authReady 这个 ref 做标记。只有 authReady 为 true（initAuth 已完成）时，才执行真正的认证检查和角色权限判断。之前的导航一律放行。

---

## 11. 67KB 的 Store 怎么避免维护问题？

几个方面：

逻辑分组。虽然是单一 store，但内部按功能域组织代码——认证相关（login、logout、initAuth）、CRUD 操作（handleEntitySubmit、handlePaperSubmit）、AI 功能（handleAiGenerate、handleAiChat）、考试流程（startExam、handleExamSubmitted）等，每组代码用注释块分隔。

computed 派生状态。不在 actions 中手动同步冗余数据，而是用 computed 自动派生。比如 menuItems 根据 role 计算，isAdmin 根据 role 计算，避免状态不一致。

TypeScript 约束。所有 state 都有明确的类型注解，BootstrapData 接口定义了完整的数据结构，任何类型不匹配的操作在编译期就会被发现。

如果继续增长，改进方向是：拆分为多个独立 store（authStore 管理登录状态、examStore 管理考试流程、aiStore 管理 AI 功能），各 store 通过 import 互相引用。Pinia 天然支持多 store，不需要额外配置。

---

## 12. 防切屏检测怎么实现的？

在 ExamSessionModal 组件的 onMounted 中注册了 `document.addEventListener('visibilitychange', handleVisibilityChange)`。

handleVisibilityChange 的逻辑：当 `document.visibilityState === 'hidden'`（用户切换到其他标签页或最小化浏览器），switchCount 加 1，然后调用 saveDraft 保存当前答案到后端。接着检查 `switchCount > exam.antiCheatLimit`，如果超过阈值，自动调用 submitPaper 强制交卷。

同时用 setInterval 每秒检查倒计时，如果当前时间超过 deadlineAt，也自动交卷。另外每 30 秒自动保存一次答案（静默保存，不提示用户）。

组件卸载时（onBeforeUnmount）清除所有定时器和事件监听，防止内存泄漏。

侧边栏显示切屏次数和阈值（如 "3 / 5"），超过阈值时数字变红色警告。

---

## 13. SSE 流式接口怎么在前端处理的？

我封装了一个通用的 sseStream 函数，所有 AI 流式功能（AI 出题、AI 练习、AI 聊天）都调用这个函数。

整体流程：

1. 用 fetch 发 POST 请求到后端 SSE 端点，拿到 Response 对象。

2. 通过 `response.body.getReader()` 获取 ReadableStream 的 reader，用 TextDecoder 逐块解码。

3. 维护一个 buffer，每次读取的数据追加到 buffer，按换行符分割。最后一个不完整的行留在 buffer 等下次拼接。

4. 解析 SSE 协议：识别 `event:` 行和 `data:` 行。遇到空行表示一个事件结束，此时解析 data 的 JSON，根据 event 类型（chunk / complete / error）调用对应的回调函数。

5. chunk 事件包含 { content, reasoning } 字段，onChunk 回调将 content 实时追加到界面上的消息气泡中，实现逐字输出效果。

额外做了几个健壮性处理：

- 心跳超时：30 秒没收到任何数据，自动断开（controller.abort()），避免连接假死。
- 自动重试：收到 429 或 503 时，指数退避 + 随机抖动后重试，最多 2 次。网络错误也做重试。
- AbortController：返回 controller 给调用方，用户点击"停止生成"时调用 controller.abort() 中断连接。AbortError 静默处理，不弹错误提示。
- AI 思考过滤：AI 模型有时会在 content 中输出推理过程，用 looksLikeThinking 函数检测并剥离到 reasoning 字段。

---

## 14. 自研 xlsx 解析器的实现？为什么不用 SheetJS？

xlsx 文件本质上是一个 ZIP 压缩包，里面包含多个 XML 文件。核心数据在两个文件中：

- xl/sharedStrings.xml：所有单元格的字符串值（共享字符串表，避免重复存储）
- xl/worksheets/sheet1.xml：单元格的位置和值引用（如 `<c r="A1" t="s"><v>3</v></c>` 表示 A1 单元格引用共享字符串表第 3 项）

实现步骤：

1. 用 JSZip 或手动实现 ZIP 解压（ZIP 格式是公开标准，基于 deflate 算法）。
2. 解析 sharedStrings.xml，提取所有字符串到数组。
3. 解析 sheet1.xml，遍历每个 `<c>` 元素，根据 `t` 属性判断类型（s = 共享字符串引用，n = 数字），从对应表中取值。
4. 将单元格位置（如 "A1"）转换为行列索引，组装成二维数组。

不用 SheetJS（xlsx 库）的原因是减少包体积和第三方依赖。SheetJS 社区版有 400KB+，而项目中只需要解析简单的学生/教师名单表格（姓名、学号、班级等固定列），自己实现足够且只有几十 KB。

---

## 15. 响应式布局怎么实现的？断点怎么设的？

通过 useDeviceType composable 实现。它监听 window 的 resize 和 orientationchange 事件，用 requestAnimationFrame 做防抖（避免高频触发），根据 window.innerWidth 划分三档：

- isMobile：宽度 < 768px
- isTablet：768px ≤ 宽度 ≤ 1024px
- isDesktop：宽度 > 1024px

然后 useResponsiveLayout composable 基于 useDeviceType 的结果派生布局决策：

- tableMode：mobile 用 cards（卡片式），tablet 用 compact（紧凑表格），desktop 用 full（完整表格含所有列）
- formLayout：mobile 用 vertical（表单项纵向排列），其他用 grid（网格排列）
- sidebarMode：mobile 用 drawer（侧边栏隐藏，汉堡菜单触发抽屉），tablet 用 collapsed（侧边栏折叠只显示图标），desktop 用 expanded（完整展开）

组件中只需要调用 `const { tableMode, formLayout } = useResponsiveLayout()`，根据返回值渲染不同的 UI 结构。

---

## 16. Flex 和 Grid 的区别？项目中分别在哪里用了？

Flex 是一维布局，处理一个方向（行或列）的元素排列。适合导航栏、按钮组、表单控件等线性排列场景。

Grid 是二维布局，同时控制行和列。适合页面整体框架、卡片网格、复杂表单等需要行列对齐的场景。

项目中的使用：

- Grid：AppShell 组件的页面整体框架用 `display: grid; grid-template-columns: 220px 1fr`，左侧固定宽度侧边栏，右侧弹性内容区。
- Flex：侧边栏内部用 `flex-direction: column` 纵向排列菜单项；考试界面的题目导航按钮用 Flex 横向排列；弹窗内部的表单控件用 Flex 布局。
- 考试界面整体用 Grid（`grid-template-columns: 280px 1fr`，左侧信息面板 + 右侧题目区域）。

简单说，页面骨架用 Grid，局部排列用 Flex。

---

## 17. Vite 为什么比 Webpack 快？开发模式和生产模式怎么工作？

Vite 开发模式利用浏览器原生 ES Module（ESM）。它不做打包，而是将源码文件直接以 ESM 形式提供给浏览器。浏览器请求一个模块时，Vite 开发服务器实时编译该文件并返回，按需编译所以启动几乎是瞬时的。修改代码时只需要重新编译被修改的模块，通过 HMR（Hot Module Replacement）精准更新，不需要重新构建整个依赖图。

Vite 生产模式使用 Rollup 做打包。Rollup 的 tree-shaking 能力更强（基于 ESM 静态分析），可以做更精确的死代码消除。生产构建会做代码分割（dynamic import 自动拆包）、资源内联、CSS 提取等优化。

我在项目中的 Vite 配置：
- 用 `@vitejs/plugin-vue` 插件处理 .vue 单文件组件
- 配置 `resolve.alias` 将 `@` 映射到 `src` 目录，import 时写 `@/components/...` 而不用相对路径
- 配置 `server.proxy` 将 `/api` 请求代理到后端 `http://127.0.0.1:8080`，解决开发环境跨域问题

---

## 18. 页面变多、组件变复杂后怎么做性能优化？

几个方向：

路由级懒加载。我已经在使用：`component: () => import('@/views/teacher/QuestionBank.vue')`，访问到对应路由时才加载组件代码，首屏不需要加载所有页面。

组件级缓存。对于切换频繁但数据不变的组件（如弹窗），可以用 KeepAlive 缓存实例，避免重复创建和销毁。

列表渲染优化。题库有 1000+ 道题，如果全部渲染会卡顿。可以做虚拟滚动（只渲染可视区域内的 DOM 元素），或者像我项目中做的，后端分页 + 前端分页组件配合，每页只加载 50 条。

计算优化。用 computed 缓存派生数据，避免在模板中写复杂表达式（每次渲染都会重新计算）。大数组的排序/过滤在数据变化时计算一次缓存结果，而不是每次渲染时重算。

事件优化。滚动、resize 等高频事件做防抖/节流。我项目中 useDeviceType 用 requestAnimationFrame 做防抖，避免 resize 事件高频触发。

资源优化。图片懒加载、ECharts 图表按需引入（而不是引入完整库）、CSS 提取到单独文件利用浏览器缓存。

---

## 19. 浏览器输入 URL 到页面渲染的过程？

DNS 解析：浏览器查找域名对应的 IP 地址，依次查浏览器缓存、系统缓存、本地 hosts 文件、路由器缓存、ISP DNS 服务器、根域名服务器。

TCP 连接：通过三次握手建立 TCP 连接（SYN → SYN+ACK → ACK）。如果是 HTTPS，还需要 TLS 握手。

HTTP 请求：浏览器发送 HTTP 请求，包含请求方法、URL、请求头（Cookie、Accept 等）。

服务器处理并返回：服务器处理请求，返回 HTTP 响应（状态码、响应头、HTML 内容）。

浏览器解析渲染：
1. 解析 HTML 构建 DOM 树
2. 解析 CSS 构建 CSSOM 树
3. DOM + CSSOM 合成 Render Tree（渲染树）
4. Layout（布局）：计算每个节点的几何信息（位置、大小）
5. Paint（绘制）：将节点绘制为像素
6. Composite（合成）：将不同图层合成为最终的页面

对于 Vite 开发模式，HTML 中的 `<script type="module" src="/src/main.ts">` 会触发浏览器向 Vite 服务器请求 main.ts，Vite 实时编译后返回 JS 代码，JS 中 import 的模块又会触发更多请求（浏览器 ESM 按需拉取），形成瀑布式请求链。

---

## 20. API 客户端怎么管理认证状态的？Token 放在哪里？

我用了一个模块级变量 `let currentAuthToken = ''` 存储当前用户的认证标识。登录成功后调用 `setCurrentAuthToken(user.id)` 设置值，退出时清空。

每次请求在 request 函数中检查 currentAuthToken，如果有值就在请求头中设置 `X-User-Id: token`。

当前实现用的是简化的 Bearer Token（用户 ID），没有用 JWT。面试官可能会追问的问题：

为什么不用 localStorage 存 token？当前方案用模块变量，刷新页面后会丢失，需要重新登录。改进方案：登录成功后将 token 存入 localStorage，应用启动时从 localStorage 恢复。但要注意 XSS 攻击可以读取 localStorage。

更安全的方案是用 httpOnly Cookie 存储 token，前端 JS 无法读取，防止 XSS 窃取。配合 CSRF Token 防跨站请求伪造。

生产环境应该用 JWT + Spring Security：登录返回 JWT（包含用户 ID、角色、过期时间），前端每次请求在 Authorization 头中带 `Bearer <jwt>`，后端验证签名和有效期。

---

## 21. HTTP 429 和 503 分别代表什么？怎么处理的？

429 Too Many Requests：服务器限流，客户端在单位时间内请求次数过多。
503 Service Unavailable：服务器暂时无法处理请求，通常是过载或维护中。

在项目中这两个状态码主要出现在 AI 功能接口中（AI 出题、AI 练习等），因为 AI 服务有并发限制。

request 函数中的处理：

```typescript
if (response.status === 429) {
  errorMessage = "AI 服务当前请求过多，请稍等片刻再试";
} else if (response.status === 503) {
  errorMessage = "AI 服务暂时不可用，请稍后重试";
}
```

将技术性的 HTTP 状态码转为用户能理解的中文提示，通过 Toast 通知系统弹出。

在 SSE 流式请求中处理更完善：

收到 429 或 503 时，不是直接报错，而是自动重试。退避策略是指数退避 + 随机抖动：`baseWait = 2000 * 2^attempt`，加上 `±25%` 的随机抖动，避免多个客户端同时重试造成雷群效应。最多重试 2 次。

还做了心跳超时检测：如果 30 秒没有收到任何 SSE 数据，自动断开连接，避免连接假死占用资源。

另外对熔断保护做了特殊处理：如果错误消息中包含"熔断保护"字样，替换为更友好的提示"AI 服务正在恢复中，请等待约30秒后重试"。
