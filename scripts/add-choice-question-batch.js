const fs = require("fs");
const path = require("path");

const storeFile = path.join(__dirname, "..", "data", "store.json");
const SOURCE_TAG = "bulk-choice-20260506";
const SINGLE_COUNT = 500;
const MULTIPLE_COUNT = 200;

const topics = [
  ["Java 基础", "StringBuilder", "StringBuilder", "高效拼接可变字符串", "循环拼接日志或 SQL 片段", "append"],
  ["Java 基础", "泛型", "泛型", "在编译期约束集合或方法的类型", "公共工具类和集合返回值声明", "<T>"],
  ["Java 基础", "异常处理", "try-catch-finally", "捕获异常并保证必要清理逻辑执行", "文件读取、网络请求和事务处理", "finally"],
  ["Java 基础", "枚举", "enum", "表达固定且有限的业务状态", "考试状态、题型和用户角色建模", "enum"],
  ["Java 基础", "注解", "Annotation", "为类、方法或字段提供元数据", "接口校验、依赖注入和路由映射", "@interface"],
  ["Java 基础", "反射", "Reflection", "在运行期读取类结构并动态调用成员", "框架扫描 Bean 或通用对象映射", "Class"],
  ["Java 基础", "Lambda", "Lambda 表达式", "用更简洁的函数式写法传递行为", "集合排序、过滤和回调处理", "->"],
  ["Java 集合", "ArrayList", "ArrayList", "按下标快速读取有序元素", "课程列表、菜单配置和顺序数据展示", "get"],
  ["Java 集合", "LinkedList", "LinkedList", "在链式结构上处理频繁头尾插入", "任务队列和双端队列操作", "addFirst"],
  ["Java 集合", "HashMap", "HashMap", "按键值对组织数据并快速查找", "按用户 ID 查询用户资料", "put/get"],
  ["Java 集合", "HashSet", "HashSet", "保存不重复元素并完成去重", "导入账号、标签集合和名单去重", "add"],
  ["Java 集合", "TreeMap", "TreeMap", "按键的自然顺序或比较器排序", "按日期或编号有序输出统计结果", "Comparator"],
  ["Java 集合", "ConcurrentHashMap", "ConcurrentHashMap", "在并发场景下安全维护键值数据", "缓存在线会话和并发统计计数", "computeIfAbsent"],
  ["Java 并发", "线程池", "ThreadPoolExecutor", "复用线程并统一调度异步任务", "批量导出、异步通知和后台计算", "submit"],
  ["Java 并发", "synchronized", "synchronized", "保护临界区并保证同一时刻互斥访问", "库存扣减和共享计数器更新", "synchronized"],
  ["Java 并发", "显式锁", "ReentrantLock", "用显式加锁和释放控制同步范围", "需要 tryLock 或多个条件队列的同步逻辑", "lock/unlock"],
  ["Java 并发", "volatile", "volatile", "保证共享变量修改对其他线程可见", "停止标记和轻量状态同步", "volatile"],
  ["Java 并发", "CompletableFuture", "CompletableFuture", "编排异步任务并组合多个结果", "并行查询学生、试卷和成绩数据", "thenApply"],
  ["Java 并发", "CountDownLatch", "CountDownLatch", "等待多个子任务完成后继续执行", "批量任务汇总和并发测试起跑控制", "countDown"],
  ["JVM", "堆内存", "JVM 堆", "存放大多数对象实例并接受垃圾回收管理", "排查内存溢出和对象生命周期问题", "Heap"],
  ["JVM", "类加载", "类加载机制", "把 class 字节码加载并转换为运行期类型信息", "分析框架启动和插件加载过程", "ClassLoader"],
  ["JVM", "GC Roots", "可达性分析", "从根对象出发判断对象是否仍可访问", "定位静态集合持有对象导致的内存泄漏", "GC Roots"],
  ["JVM", "JIT", "JIT 编译", "把热点字节码优化为本地机器码执行", "分析长期运行服务的性能变化", "Just In Time"],
  ["数据库", "事务隔离", "事务隔离级别", "控制并发事务之间的数据可见性", "解决脏读、不可重复读和幻读问题", "Isolation"],
  ["数据库", "索引", "B+Tree 索引", "减少扫描范围并提升查询效率", "按学号、考试 ID 或提交时间查询", "INDEX"],
  ["数据库", "联合索引", "联合索引", "按多个字段组合建立查询加速路径", "班级、考试、状态组合筛选", "最左前缀"],
  ["数据库", "SQL JOIN", "JOIN", "把多张表中有关联的数据组合查询", "查询学生姓名、班级和成绩明细", "INNER JOIN"],
  ["数据库", "视图", "View", "封装常用查询并提供稳定读取入口", "成绩汇总报表和只读统计页面", "CREATE VIEW"],
  ["MySQL", "EXPLAIN", "EXPLAIN", "查看 SQL 执行计划并定位性能问题", "分析慢查询是否命中索引", "EXPLAIN"],
  ["MySQL", "慢查询", "慢查询日志", "记录执行时间超过阈值的 SQL", "排查成绩分析页面响应慢的问题", "slow_query_log"],
  ["Web", "HTTP 状态码", "HTTP 状态码", "用标准数字表达请求处理结果", "区分登录失败、无权限和资源不存在", "200/401/404"],
  ["Web", "Cookie", "Cookie", "在浏览器端保存随请求发送的小段状态", "保存会话标识或偏好设置", "Set-Cookie"],
  ["Web", "Session", "Session", "在服务端维护用户会话状态", "登录后识别当前用户身份", "sessionId"],
  ["Web", "JWT", "JWT", "用签名令牌携带可验证的身份声明", "前后端分离接口鉴权", "token"],
  ["Web", "CORS", "CORS", "控制浏览器跨域请求是否被允许", "前端开发服务器访问后端 API", "Access-Control-Allow-Origin"],
  ["Web", "RESTful API", "RESTful API", "用资源路径和 HTTP 方法表达接口语义", "题库、试卷和考试的增删改查接口", "GET/POST/PUT/DELETE"],
  ["Spring", "IoC", "IoC 容器", "统一创建和装配对象依赖", "Service、Repository 和 Controller 协作", "@Autowired"],
  ["Spring", "AOP", "AOP 切面", "把日志、事务等横切逻辑从业务中抽离", "接口耗时统计和权限校验", "@Aspect"],
  ["Spring", "Bean 生命周期", "Bean 生命周期", "描述对象从创建到销毁的回调过程", "初始化连接池或释放外部资源", "init/destroy"],
  ["Spring", "事务传播", "事务传播行为", "定义方法嵌套调用时事务如何参与或新建", "下单、扣库存和写日志的一致性控制", "Propagation"],
  ["Spring MVC", "Controller", "Controller", "接收请求并协调业务处理与响应返回", "考试提交、题库导入和成绩查询接口", "@RequestMapping"],
  ["Spring", "Validation", "参数校验", "在进入业务逻辑前验证输入合法性", "新增题目、发布考试和批量导入", "@Valid"],
  ["前端", "Vue 响应式", "Vue 响应式", "让状态变化自动驱动界面更新", "筛选条件变化后刷新题目列表", "ref/reactive"],
  ["前端", "Vue Router", "Vue Router", "管理前端页面路径和视图切换", "教师端不同功能页面导航", "router"],
  ["前端", "Pinia", "Pinia", "集中管理跨组件共享状态", "保存当前用户、菜单和考试会话信息", "store"],
  ["前端", "TypeScript", "类型保护", "在运行分支中缩小联合类型范围", "处理不同题型的答案结构", "typeof"],
  ["前端", "Promise", "Promise", "表达异步操作最终成功或失败的结果", "请求 API 后更新页面状态", "then/catch"],
  ["前端", "ECharts", "ECharts option", "用配置对象声明图表结构和数据系列", "成绩趋势、班级对比和题型正确率展示", "setOption"],
  ["软件工程", "RBAC", "RBAC 权限模型", "按角色控制用户可访问的功能范围", "管理员、教师和学生菜单隔离", "role"],
  ["在线考试", "错题本", "错题本", "沉淀答错题目并支持重做复习", "学生复盘薄弱知识点", "wrongBook"],
];

function readStore() {
  return JSON.parse(fs.readFileSync(storeFile, "utf8"));
}

function writeStore(store) {
  fs.writeFileSync(storeFile, JSON.stringify(store, null, 2), "utf8");
}

function rotate(list, offset) {
  const normalized = ((offset % list.length) + list.length) % list.length;
  return list.slice(normalized).concat(list.slice(0, normalized));
}

function normalizeTitle(value) {
  return String(value || "").trim().replace(/\s+/g, " ");
}

function conceptFromTopic(row, index) {
  const [subject, knowledgePoint, term, goal, scenario, keyword] = row;
  return {
    subject,
    knowledgePoint,
    term,
    keyword,
    definition: `${term}用于${goal}，在${scenario}时可以提升实现的清晰度和稳定性。`,
    scenarioText: `${term}适合用于${scenario}。`,
    features: [
      `${term}能够${goal}`,
      `常见应用场景包括${scenario}`,
      `理解 ${keyword} 有助于掌握 ${term} 的核心用法`,
    ],
    practices: [
      `使用 ${term} 时应结合边界条件、异常处理和输入校验`,
      `${term}的实现应保持职责清晰，避免把无关逻辑混在一起`,
      `在在线考试系统中，${term}可以帮助提升功能的可维护性`,
    ],
    misconceptions: [
      `${term}会自动绕过登录和权限校验，因此不需要鉴权设计`,
      `${term}只要被引入就能自动修复所有业务逻辑错误`,
      `${term}只能在管理员端使用，教师端和学生端完全无法复用`,
      `${term}不需要任何边界处理，也不需要考虑异常情况`,
    ],
    index,
  };
}

function makeOptions(correct, distractors, seed, size = 4) {
  const unique = [correct];
  distractors.forEach((item) => {
    if (unique.length < size && item && !unique.includes(item)) {
      unique.push(item);
    }
  });
  return rotate(unique, seed);
}

function singleSpecs(concept, allConcepts) {
  const otherDefinitions = allConcepts
    .filter((item) => item.term !== concept.term)
    .map((item) => item.definition);
  const otherScenarios = allConcepts
    .filter((item) => item.term !== concept.term)
    .map((item) => item.scenarioText);
  const wrongs = concept.misconceptions;
  return [
    {
      title: `扩展单选：${concept.term}的核心作用是什么？`,
      answer: concept.definition,
      distractors: [...otherDefinitions, ...wrongs],
    },
    {
      title: `扩展单选：${concept.term}更适合应用在哪个场景？`,
      answer: concept.scenarioText,
      distractors: [...otherScenarios, ...wrongs],
    },
    {
      title: `扩展单选：学习${concept.term}时应重点理解哪一项？`,
      answer: `应重点理解 ${concept.keyword} 及其在业务代码中的使用边界。`,
      distractors: [
        `只需要记住 ${concept.term} 的中文名称即可，不必理解使用条件。`,
        `${concept.term}主要用于绕过接口参数校验。`,
        `${concept.term}只和页面颜色有关，与业务逻辑无关。`,
        ...wrongs,
      ],
    },
    {
      title: `扩展单选：关于${concept.term}的说法，哪一项属于常见误区？`,
      answer: wrongs[0],
      distractors: [...concept.features, ...concept.practices],
    },
    {
      title: `扩展单选：在教师端维护题库时，${concept.term}带来的主要价值是？`,
      answer: `帮助教师端功能保持结构清晰，并降低后续维护成本。`,
      distractors: [
        "让所有题目自动变成满分答案。",
        "强制删除学生的历史答题记录。",
        "完全替代数据库和接口设计。",
        ...wrongs,
      ],
    },
    {
      title: `扩展单选：使用${concept.term}时更合理的工程做法是？`,
      answer: concept.practices[0],
      distractors: [
        "不做输入校验，直接信任所有客户端数据。",
        "把所有页面逻辑都写进一个超长函数。",
        "只在出现线上故障后再补充异常处理。",
        ...wrongs,
      ],
    },
    {
      title: `扩展单选：${concept.term}与在线考试业务结合时，优先关注什么？`,
      answer: "优先关注角色权限、数据一致性、异常提示和可追踪日志。",
      distractors: [
        "优先隐藏所有错误信息，使用户无法判断操作结果。",
        "优先跳过后端校验，只依赖前端按钮禁用。",
        "优先把所有账号密码写在页面脚本中。",
        ...wrongs,
      ],
    },
    {
      title: `扩展单选：${concept.term}相关实现需要避免哪种问题？`,
      answer: "需要避免职责混乱、重复代码和缺少失败分支处理。",
      distractors: [
        "需要避免任何日志记录，因为日志一定会导致系统不可用。",
        "需要避免所有参数校验，因为校验会让功能无法运行。",
        "需要避免接口返回明确状态，因为状态码没有意义。",
        ...wrongs,
      ],
    },
    {
      title: `扩展单选：如果要验证${concept.term}实现是否可靠，最应检查哪项？`,
      answer: "检查正常流程、异常流程、边界输入和权限范围是否都有覆盖。",
      distractors: [
        "只检查按钮颜色是否足够醒目。",
        "只检查文件名是否足够短。",
        "只检查是否删除了所有历史数据。",
        ...wrongs,
      ],
    },
    {
      title: `扩展单选：关于${concept.term}的知识点归类，哪一项最准确？`,
      answer: `${concept.term}属于${concept.subject} / ${concept.knowledgePoint}相关知识点。`,
      distractors: [
        `${concept.term}只属于图片处理知识点，与软件开发无关。`,
        `${concept.term}只属于线下阅卷规则，与系统实现无关。`,
        `${concept.term}只属于硬件采购流程，与代码设计无关。`,
        ...wrongs,
      ],
    },
  ];
}

function multipleSpecs(concept) {
  return [
    {
      title: `扩展多选：关于${concept.term}的核心特征，哪些说法正确？`,
      answers: concept.features,
      options: [...concept.features, concept.misconceptions[0], concept.misconceptions[1]],
    },
    {
      title: `扩展多选：在教师端使用${concept.term}时，哪些做法更合理？`,
      answers: concept.practices,
      options: [...concept.practices, concept.misconceptions[2], concept.misconceptions[3]],
    },
    {
      title: `扩展多选：${concept.term}用于在线考试系统时，哪些关注点是正确的？`,
      answers: [
        "保持接口返回清晰，便于前端给出明确反馈",
        "保留关键操作日志，便于后续排查和审计",
        "结合角色权限控制，避免越权访问数据",
      ],
      options: [
        "保持接口返回清晰，便于前端给出明确反馈",
        "保留关键操作日志，便于后续排查和审计",
        "结合角色权限控制，避免越权访问数据",
        "把所有用户密码展示在教师端列表中",
        "跳过后端校验，只依赖浏览器页面限制",
      ],
    },
    {
      title: `扩展多选：学习${concept.term}时，哪些判断是正确的？`,
      answers: [
        concept.definition,
        concept.scenarioText,
        `关键点可以围绕 ${concept.keyword} 展开复习`,
      ],
      options: [
        concept.definition,
        concept.scenarioText,
        `关键点可以围绕 ${concept.keyword} 展开复习`,
        concept.misconceptions[0],
        concept.misconceptions[1],
      ],
    },
  ];
}

function buildQuestion({ id, teacherId, concept, type, title, options, answer, serial }) {
  return {
    id,
    teacherId,
    subject: concept.subject,
    knowledgePoint: concept.knowledgePoint,
    difficulty: type === "single" ? (serial % 3 === 0 ? "中" : "易") : serial % 4 === 0 ? "难" : "中",
    type,
    title,
    options,
    answer,
    score: 4,
    sourceTag: SOURCE_TAG,
  };
}

function stripPreviousBatch(store) {
  const removedIds = new Set((store.questions || []).filter((item) => item.sourceTag === SOURCE_TAG).map((item) => item.id));
  store.questions = (store.questions || []).filter((item) => item.sourceTag !== SOURCE_TAG);
  store.papers = (store.papers || []).map((paper) => ({
    ...paper,
    questionIds: (paper.questionIds || []).filter((id) => !removedIds.has(id)),
  }));
  store.submissions = (store.submissions || []).map((submission) => ({
    ...submission,
    answers: (submission.answers || []).filter((item) => !removedIds.has(item.questionId)),
    answerDetail: (submission.answerDetail || []).filter((item) => !removedIds.has(item.questionId)),
  }));
  store.wrongBookEntries = (store.wrongBookEntries || []).filter((item) => !removedIds.has(item.questionId));
  return removedIds.size;
}

function buildBatch(store, teacherId) {
  const concepts = topics.map(conceptFromTopic);
  const existingIds = new Set((store.questions || []).map((item) => item.id));
  const existingTitles = new Set((store.questions || []).map((item) => normalizeTitle(item.title)));
  const batch = [];
  let singleSerial = 1;
  let multipleSerial = 1;

  concepts.forEach((concept) => {
    singleSpecs(concept, concepts).forEach((spec) => {
      const id = `bulk-single-${String(singleSerial).padStart(4, "0")}`;
      if (batch.filter((item) => item.type === "single").length >= SINGLE_COUNT) return;
      if (existingIds.has(id) || existingTitles.has(normalizeTitle(spec.title))) {
        throw new Error(`发现重复单选题：${spec.title}`);
      }
      const options = makeOptions(spec.answer, spec.distractors, singleSerial, 4);
      batch.push(
        buildQuestion({
          id,
          teacherId,
          concept,
          type: "single",
          title: spec.title,
          options,
          answer: [spec.answer],
          serial: singleSerial,
        })
      );
      existingIds.add(id);
      existingTitles.add(normalizeTitle(spec.title));
      singleSerial += 1;
    });
  });

  concepts.forEach((concept) => {
    multipleSpecs(concept).forEach((spec) => {
      const id = `bulk-multiple-${String(multipleSerial).padStart(4, "0")}`;
      if (batch.filter((item) => item.type === "multiple").length >= MULTIPLE_COUNT) return;
      if (existingIds.has(id) || existingTitles.has(normalizeTitle(spec.title))) {
        throw new Error(`发现重复多选题：${spec.title}`);
      }
      const options = rotate([...new Set(spec.options)], multipleSerial);
      batch.push(
        buildQuestion({
          id,
          teacherId,
          concept,
          type: "multiple",
          title: spec.title,
          options,
          answer: spec.answers,
          serial: multipleSerial,
        })
      );
      existingIds.add(id);
      existingTitles.add(normalizeTitle(spec.title));
      multipleSerial += 1;
    });
  });

  const singleCount = batch.filter((item) => item.type === "single").length;
  const multipleCount = batch.filter((item) => item.type === "multiple").length;
  if (singleCount !== SINGLE_COUNT || multipleCount !== MULTIPLE_COUNT) {
    throw new Error(`生成数量不正确：single=${singleCount}, multiple=${multipleCount}`);
  }
  return batch;
}

function assertNoDuplicateQuestions(questions) {
  const ids = new Set();
  const titles = new Set();
  questions.forEach((question) => {
    if (ids.has(question.id)) {
      throw new Error(`重复题目 ID：${question.id}`);
    }
    ids.add(question.id);
    const title = normalizeTitle(question.title);
    if (titles.has(title)) {
      throw new Error(`重复题干：${question.title}`);
    }
    titles.add(title);
    if (["single", "multiple"].includes(question.type) && (!Array.isArray(question.options) || question.options.length < 4)) {
      throw new Error(`题目选项不足：${question.id}`);
    }
    if (["single", "multiple"].includes(question.type)) {
      question.answer.forEach((answer) => {
        if (!question.options.includes(answer)) {
          throw new Error(`答案不在选项中：${question.id}`);
        }
      });
    }
  });
}

function main() {
  const store = readStore();
  store.questions = Array.isArray(store.questions) ? store.questions : [];
  store.papers = Array.isArray(store.papers) ? store.papers : [];
  store.submissions = Array.isArray(store.submissions) ? store.submissions : [];
  store.wrongBookEntries = Array.isArray(store.wrongBookEntries) ? store.wrongBookEntries : [];
  store.logs = Array.isArray(store.logs) ? store.logs : [];

  const teacherId = store.users?.find((item) => item.role === "teacher")?.id || "teacher-1";
  const removedCount = stripPreviousBatch(store);
  const batch = buildBatch(store, teacherId);

  store.questions.unshift(...batch);
  store.logs.unshift({
    id: `log-bulk-choice-${Date.now()}`,
    actorId: teacherId,
    action: "批量添加选择题",
    detail: JSON.stringify({ single: SINGLE_COUNT, multiple: MULTIPLE_COUNT, removedPreviousBatch: removedCount, sourceTag: SOURCE_TAG }),
    time: new Date().toISOString(),
  });

  assertNoDuplicateQuestions(store.questions);
  writeStore(store);

  console.log(`Added ${SINGLE_COUNT} single-choice questions and ${MULTIPLE_COUNT} multiple-choice questions.`);
  console.log(`Removed previous batch: ${removedCount}`);
  console.log(`Question total: ${store.questions.length}`);
}

main();
