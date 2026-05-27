const fs = require("fs");
const path = require("path");

const storeFile = path.join(__dirname, "..", "data", "store.json");
const SOURCE_TAG = "generated-bank-20260430";
const PAPER_TAG = "comprehensive-paper-20260430";
const TOTAL_PAPERS = 10;
const PAPER_BLUEPRINT = {
  single: 5,
  multiple: 5,
  judge: 5,
  fill: 5,
  short: 2,
  coding: 1,
};

const SCORE_BY_TYPE = {
  single: 4,
  multiple: 4,
  judge: 2,
  fill: 4,
  short: 10,
  coding: 10,
};

const DIFFICULTY_BY_TYPE = {
  single: "易",
  multiple: "中",
  judge: "易",
  fill: "中",
  short: "中",
  coding: "难",
};

const concepts = [
  {
    subject: "Java",
    knowledgePoint: "StringBuilder",
    term: "StringBuilder",
    definition: "适合在循环或多次拼接场景下构造可变字符串。",
    features: ["底层维护可变字符序列", "append 调用通常不创建新的 String 对象", "频繁拼接时比直接使用加号更节省中间对象"],
    misconception: "StringBuilder 天生线程安全，适合多线程共享写入。",
    keywordPrompt: "向可变字符串末尾追加内容时常用的方法是 ____。",
    keyword: "append",
    scenario: "适合在循环拼接日志、SQL 片段或批量构造响应文本时使用。",
    codeLinePrompt: "补全把 name 追加到 StringBuilder 的关键代码。",
    codeLine: "builder.append(name);",
    codeTask: "编写一个方法，使用 StringBuilder 将列表元素按逗号拼接为单行字符串。",
  },
  {
    subject: "Java",
    knowledgePoint: "泛型",
    term: "泛型",
    definition: "用于在编译期约束集合或方法的类型，提高类型安全性。",
    features: ["可以减少强制类型转换", "常见写法如 List<String>", "有助于在编译期发现类型不匹配问题"],
    misconception: "Java 泛型会在运行时保留完整的具体类型参数，并自动生成不同字节码类。",
    keywordPrompt: "声明一个只保存整数的列表时，常见写法中的类型参数是 ____。",
    keyword: "<Integer>",
    scenario: "适合在公共工具类、集合返回值和通用组件中统一约束输入输出类型。",
    codeLinePrompt: "补全创建字符串列表的关键代码。",
    codeLine: "List<String> names = new ArrayList<>();",
    codeTask: "编写一个泛型方法，返回数组中的第一个元素。",
  },
  {
    subject: "Java",
    knowledgePoint: "equals 与 hashCode",
    term: "equals/hashCode 约定",
    definition: "用于保证对象在集合判等和哈希散列场景中的一致行为。",
    features: ["重写 equals 时通常也要重写 hashCode", "HashSet 与 HashMap 依赖 hashCode 先定位桶", "相等对象必须返回相同的 hashCode"],
    misconception: "只重写 equals 而不重写 hashCode，不会影响哈希集合中的去重结果。",
    keywordPrompt: "判断两个对象逻辑上是否相等时，优先重写的方法是 ____。",
    keyword: "equals",
    scenario: "适合实体类、值对象和需要在 Set 或 Map 中作为键使用的对象。",
    codeLinePrompt: "补全基于 id 字段比较对象是否相等的关键代码。",
    codeLine: "return Objects.equals(id, other.id);",
    codeTask: "为 Student 类实现 equals 与 hashCode，使相同学号视为同一对象。",
  },
  {
    subject: "Java 集合",
    knowledgePoint: "ArrayList",
    term: "ArrayList",
    definition: "基于动态数组实现，适合高频随机访问与顺序遍历。",
    features: ["支持通过索引快速读取元素", "尾部追加元素通常开销较低", "容量不足时会触发扩容"],
    misconception: "ArrayList 擅长在任意位置频繁插入删除，并且不会触发数组拷贝。",
    keywordPrompt: "通过下标读取 ArrayList 中元素的方法是 ____。",
    keyword: "get",
    scenario: "适合课程列表、菜单配置和需要频繁按下标读取的数据集合。",
    codeLinePrompt: "补全向列表末尾追加元素的关键代码。",
    codeLine: "list.add(item);",
    codeTask: "编写一个方法，接收 ArrayList 并返回其中的最后一个元素。",
  },
  {
    subject: "Java 集合",
    knowledgePoint: "HashMap",
    term: "HashMap",
    definition: "用于按键值对组织数据，支持根据 key 快速查找 value。",
    features: ["允许一个 null key", "常用于缓存配置和统计计数", "通过 key 的 hash 值辅助定位存储位置"],
    misconception: "HashMap 默认线程安全，可以直接在高并发写入场景下共享使用。",
    keywordPrompt: "读取键为 userId 的值时，常用方法是 ____。",
    keyword: "get",
    scenario: "适合统计词频、缓存配置项以及根据编号快速定位对象。",
    codeLinePrompt: "补全根据 key 放入值的关键代码。",
    codeLine: "map.put(key, value);",
    codeTask: "编写一个方法，使用 HashMap 统计字符串数组中每个单词出现的次数。",
  },
  {
    subject: "Java 集合",
    knowledgePoint: "HashSet",
    term: "HashSet",
    definition: "用于存放不重复元素，本质上依赖哈希结构实现去重。",
    features: ["重复元素不会被重复保存", "常用于标签去重与名单去重", "判重效果依赖元素的 equals 与 hashCode"],
    misconception: "HashSet 会自动按照插入顺序排序输出元素。",
    keywordPrompt: "向 HashSet 中添加元素时，常用方法是 ____。",
    keyword: "add",
    scenario: "适合在导入用户、收集唯一标签和处理去重名单时使用。",
    codeLinePrompt: "补全向集合中加入 code 的关键代码。",
    codeLine: "set.add(code);",
    codeTask: "编写一个方法，返回数组中去重后的元素个数。",
  },
  {
    subject: "Java",
    knowledgePoint: "Stream",
    term: "Stream 流",
    definition: "用于以声明式方式完成集合的过滤、映射、聚合等操作。",
    features: ["支持 filter 与 map 等链式处理", "常与 collect 配合生成结果集合", "有助于把数据处理流程表达得更清晰"],
    misconception: "创建 Stream 后会立刻执行所有中间操作，即使没有终止操作也会产生结果。",
    keywordPrompt: "把流结果收集成列表时，常配合的方法是 ____。",
    keyword: "collect",
    scenario: "适合对订单、成绩或日志列表做筛选、转换与统计。",
    codeLinePrompt: "补全把流结果收集为列表的关键代码。",
    codeLine: "stream.collect(Collectors.toList());",
    codeTask: "编写一个方法，使用 Stream 过滤出所有及格成绩并返回新的列表。",
  },
  {
    subject: "Java",
    knowledgePoint: "Optional",
    term: "Optional",
    definition: "用于显式表达某个值可能为空，减少直接判空的嵌套代码。",
    features: ["常见方法包括 ofNullable 与 orElse", "能把缺失值处理写得更集中", "适合服务层返回可选结果"],
    misconception: "Optional 适合直接作为实体类字段长期保存，并且优于所有普通字段声明。",
    keywordPrompt: "当值可能为空时，创建 Optional 常用的方法是 ____。",
    keyword: "ofNullable",
    scenario: "适合查询结果可能不存在、配置项可能缺失的读取逻辑。",
    codeLinePrompt: "补全给空值提供默认值的关键代码。",
    codeLine: "optional.orElse(defaultValue);",
    codeTask: "编写一个方法，读取 Optional<String> 中的值；若为空则返回 defaultName。",
  },
  {
    subject: "Java 并发",
    knowledgePoint: "ThreadPoolExecutor",
    term: "线程池",
    definition: "用于复用线程并统一管理异步任务的提交、排队与执行。",
    features: ["可以限制并发线程数量", "能够降低频繁创建线程的开销", "支持队列、拒绝策略等运行参数"],
    misconception: "线程池中的线程执行完任务后会立即永久销毁，因此无法实现线程复用。",
    keywordPrompt: "向线程池提交一个任务时，常用的方法是 ____。",
    keyword: "submit",
    scenario: "适合批量导出、异步通知、并行计算和请求削峰。",
    codeLinePrompt: "补全提交 Runnable 任务的关键代码。",
    codeLine: "executor.submit(task);",
    codeTask: "编写一个方法，创建固定大小线程池并提交 5 个打印任务。",
  },
  {
    subject: "Java 并发",
    knowledgePoint: "synchronized",
    term: "synchronized",
    definition: "用于在共享资源访问前加锁，保证同一时刻只有一个线程进入临界区。",
    features: ["可用于修饰代码块或方法", "适合保护共享变量的复合操作", "进入同步块前需要先获取监视器锁"],
    misconception: "synchronized 只能修饰方法，不能作用于代码块。",
    keywordPrompt: "在方法上声明内置锁时使用的关键字是 ____。",
    keyword: "synchronized",
    scenario: "适合库存扣减、余额更新和共享计数器自增等场景。",
    codeLinePrompt: "补全同步代码块的关键写法。",
    codeLine: "synchronized (lock) { count++; }",
    codeTask: "编写一个线程安全的计数器，使用 synchronized 保证 add 方法的原子性。",
  },
  {
    subject: "Java 并发",
    knowledgePoint: "ReentrantLock",
    term: "ReentrantLock",
    definition: "提供比内置锁更灵活的显式加锁与解锁控制能力。",
    features: ["需要手动调用 lock 与 unlock", "支持 tryLock 等更灵活的获取方式", "适合需要更细粒度控制的同步场景"],
    misconception: "ReentrantLock 获取锁后无需在 finally 中释放，也不会影响后续线程。",
    keywordPrompt: "显式锁开始加锁时常用的方法是 ____。",
    keyword: "lock",
    scenario: "适合需要尝试加锁、可中断加锁或多条件队列的复杂同步逻辑。",
    codeLinePrompt: "补全在 finally 中释放锁的关键代码。",
    codeLine: "lock.unlock();",
    codeTask: "编写一个使用 ReentrantLock 保护共享余额的示例方法。",
  },
  {
    subject: "Java 并发",
    knowledgePoint: "volatile",
    term: "volatile",
    definition: "用于保证共享变量的可见性，避免线程读取到过期值。",
    features: ["写入后能较快让其他线程看到新值", "常用于状态标记位", "不能直接替代所有复合操作的原子性控制"],
    misconception: "volatile 可以保证 i++ 这类复合操作在多线程下天然原子。",
    keywordPrompt: "声明线程停止标记位时常用的关键字是 ____。",
    keyword: "volatile",
    scenario: "适合退出开关、配置热更新标记和轻量状态同步。",
    codeLinePrompt: "补全声明共享运行标记的关键代码。",
    codeLine: "private volatile boolean running = true;",
    codeTask: "编写一个线程轮询 running 标记并安全退出的示例。",
  },
  {
    subject: "JVM",
    knowledgePoint: "堆与栈",
    term: "JVM 堆栈模型",
    definition: "用于区分对象实例存放区域与方法调用、局部变量等执行期数据区域。",
    features: ["对象实例通常分配在堆上", "方法调用会使用栈帧保存局部变量等信息", "理解堆栈有助于分析内存问题"],
    misconception: "所有对象和局部变量都只会保存在同一个方法栈中。",
    keywordPrompt: "Java 对象实例默认主要分配在 ____ 中。",
    keyword: "堆",
    scenario: "适合在分析内存溢出、栈溢出和对象生命周期时建立整体认知。",
    codeLinePrompt: "补全创建对象实例的关键代码。",
    codeLine: "User user = new User();",
    codeTask: "结合一个简单方法调用链，说明局部变量、对象引用与对象实例分别位于哪里。",
  },
  {
    subject: "JVM",
    knowledgePoint: "双亲委派",
    term: "双亲委派模型",
    definition: "用于在类加载时优先委托父加载器，避免基础类被重复加载或被错误替换。",
    features: ["会先向上委托父加载器", "有助于保证核心类加载的稳定性", "能降低同名类冲突的风险"],
    misconception: "自定义类加载器加载任何类时都必须直接绕过父加载器，不能委托。",
    keywordPrompt: "类加载请求会优先委托给 ____ 加载器链处理。",
    keyword: "父",
    scenario: "适合理解 SPI、热部署、自定义类加载器和核心类安全边界。",
    codeLinePrompt: "补全获取当前类加载器的关键代码。",
    codeLine: "ClassLoader loader = Demo.class.getClassLoader();",
    codeTask: "说明自定义类加载器中何时适合保留双亲委派，何时可能选择打破默认委派。",
  },
  {
    subject: "JVM",
    knowledgePoint: "可达性分析",
    term: "可达性分析",
    definition: "用于从 GC Roots 出发判断对象是否仍可被访问，从而决定是否可回收。",
    features: ["会从 GC Roots 作为起点搜索引用链", "不可达对象才有机会被回收", "理解该机制有助于排查内存泄漏"],
    misconception: "只要对象已经不在当前方法中使用，就一定会立刻被垃圾回收。",
    keywordPrompt: "垃圾回收判断对象是否存活时，常从 ____ 出发分析引用链。",
    keyword: "GC Roots",
    scenario: "适合分析静态集合持有对象、监听器未注销等内存泄漏问题。",
    codeLinePrompt: "补全把对象引用置空的关键代码。",
    codeLine: "user = null;",
    codeTask: "结合一个静态列表未清理元素的例子，说明为什么对象可能始终可达。",
  },
  {
    subject: "Spring",
    knowledgePoint: "IoC",
    term: "IoC 容器",
    definition: "用于统一管理对象创建、装配和生命周期，降低组件之间的耦合度。",
    features: ["对象实例通常交由容器创建", "依赖关系可以通过注入方式组装", "便于测试和模块解耦"],
    misconception: "使用 Spring 后，所有依赖都必须通过 new 手动创建，容器只负责日志输出。",
    keywordPrompt: "在 Spring 中按类型注入依赖时，常见注解是 ____。",
    keyword: "@Autowired",
    scenario: "适合服务层、控制层、仓储层等多组件协作的项目。",
    codeLinePrompt: "补全声明 Service 组件的关键注解。",
    codeLine: "@Service",
    codeTask: "编写一个 UserService 与 UserRepository 的最小示例，使用 IoC 完成依赖注入。",
  },
  {
    subject: "Spring",
    knowledgePoint: "AOP",
    term: "AOP 切面",
    definition: "用于把日志、事务、鉴权等横切关注点从业务代码中抽离出来。",
    features: ["常见切点包括方法执行前后", "适合统一处理日志与权限", "可以减少重复样板代码"],
    misconception: "AOP 只能处理前端页面样式，无法作用于 Java 方法调用。",
    keywordPrompt: "在 Spring 中声明一个切面类时，常用注解是 ____。",
    keyword: "@Aspect",
    scenario: "适合统一记录接口耗时、异常日志与权限校验。",
    codeLinePrompt: "补全定义前置通知的关键注解。",
    codeLine: "@Before(\"execution(* com.demo..*(..))\")",
    codeTask: "编写一个切面，为 service 层方法打印调用开始与结束日志。",
  },
  {
    subject: "Spring Boot",
    knowledgePoint: "Starter",
    term: "Starter 依赖",
    definition: "用于按场景打包常见依赖与默认配置，简化项目集成过程。",
    features: ["减少手动拼装依赖的成本", "常与自动配置机制配合使用", "便于快速搭建标准化应用"],
    misconception: "引入 Starter 后仍必须手动复制全部底层依赖，否则项目无法启动。",
    keywordPrompt: "Web 项目常见的 Spring Boot 场景启动依赖是 ____。",
    keyword: "spring-boot-starter-web",
    scenario: "适合快速搭建 REST API、后台管理系统和微服务应用。",
    codeLinePrompt: "补全 Maven 中引入 Web Starter 的 artifactId。",
    codeLine: "<artifactId>spring-boot-starter-web</artifactId>",
    codeTask: "搭建一个最小的 Spring Boot Web 项目，并提供 /hello 接口返回字符串。",
  },
  {
    subject: "Java Web",
    knowledgePoint: "DispatcherServlet",
    term: "DispatcherServlet",
    definition: "是 Spring MVC 的前端控制器，负责统一接收请求并分发到处理器。",
    features: ["统一入口有助于集中处理 Web 请求", "会协调处理器映射、参数绑定与视图解析", "是请求分发流程中的核心组件"],
    misconception: "DispatcherServlet 只负责数据库连接，和 HTTP 请求路由无关。",
    keywordPrompt: "Spring MVC 的核心前端控制器名称是 ____。",
    keyword: "DispatcherServlet",
    scenario: "适合理解请求从浏览器进入控制层的完整链路。",
    codeLinePrompt: "补全声明处理 GET 请求的关键注解。",
    codeLine: "@GetMapping(\"/users\")",
    codeTask: "说明一次 GET 请求在 Spring MVC 中经过 DispatcherServlet、Controller 和 View 的主要流转过程。",
  },
  {
    subject: "Java Web",
    knowledgePoint: "Filter",
    term: "Servlet Filter",
    definition: "用于在请求进入 Servlet 前后执行通用预处理或后处理逻辑。",
    features: ["可用于编码设置与跨域处理", "能在目标资源前后插入公共逻辑", "适合做统一日志和权限初筛"],
    misconception: "Filter 只能在 Controller 执行结束后读取结果，无法在请求到达前介入。",
    keywordPrompt: "在 Java Web 中声明过滤器接口时，核心类型是 ____。",
    keyword: "Filter",
    scenario: "适合统一设置字符编码、记录访问日志和做基础鉴权。",
    codeLinePrompt: "补全继续放行请求的关键代码。",
    codeLine: "chain.doFilter(request, response);",
    codeTask: "编写一个过滤器，为所有请求统一设置 UTF-8 编码并打印访问路径。",
  },
  {
    subject: "Java Web",
    knowledgePoint: "Interceptor",
    term: "HandlerInterceptor",
    definition: "用于在 Spring MVC 请求处理前后拦截控制器调用，便于做更靠近业务层的通用控制。",
    features: ["可在 preHandle 中决定是否继续执行", "适合登录校验和接口耗时统计", "通常基于 Spring MVC 配置注册路径"],
    misconception: "拦截器只能拦截静态资源文件，无法参与 Controller 请求链。",
    keywordPrompt: "Spring MVC 拦截器在放行请求时，preHandle 通常返回 ____。",
    keyword: "true",
    scenario: "适合对后台接口做登录态校验、角色校验与链路日志记录。",
    codeLinePrompt: "补全拦截器中放行请求的返回代码。",
    codeLine: "return true;",
    codeTask: "编写一个登录拦截器，未登录时拦截 /admin/** 请求并返回 401。",
  },
  {
    subject: "Java Web",
    knowledgePoint: "HttpSession",
    term: "HttpSession",
    definition: "用于在同一用户的多次请求之间保存服务端会话数据。",
    features: ["常用于保存登录态与临时会话信息", "数据保存在服务端", "浏览器可通过会话标识关联到对应 Session"],
    misconception: "Session 数据默认完全保存在浏览器本地，因此服务端重启不会受影响。",
    keywordPrompt: "向 Session 中存储登录用户时常用的方法是 ____。",
    keyword: "setAttribute",
    scenario: "适合后台管理系统、购物车和需要短期会话状态的业务。",
    codeLinePrompt: "补全读取 Session 中 user 的关键代码。",
    codeLine: "session.getAttribute(\"user\");",
    codeTask: "编写一个登录接口，登录成功后把当前用户信息写入 Session。",
  },
  {
    subject: "Java Web",
    knowledgePoint: "Cookie",
    term: "Cookie",
    definition: "用于在浏览器端保存少量键值信息，并随同同域请求自动发送给服务器。",
    features: ["适合保存轻量标识信息", "可以设置过期时间", "常与 Session 标识或偏好设置配合使用"],
    misconception: "Cookie 可以安全地直接保存完整用户密码，而且不会被浏览器看到。",
    keywordPrompt: "创建名为 token 的 Cookie 时常用的类型是 ____。",
    keyword: "Cookie",
    scenario: "适合保存登录票据、语言偏好或主题设置等轻量信息。",
    codeLinePrompt: "补全把 Cookie 写回响应头的关键代码。",
    codeLine: "response.addCookie(cookie);",
    codeTask: "编写一个示例，把主题颜色保存到 Cookie，并在下一次请求中读取。",
  },
  {
    subject: "MyBatis",
    knowledgePoint: "Mapper",
    term: "Mapper 接口",
    definition: "用于把 Java 方法与 SQL 语句映射起来，简化持久层访问代码。",
    features: ["接口方法可直接对应 SQL 语句", "常与 XML 或注解配置配合", "有助于隔离数据库访问逻辑"],
    misconception: "使用 MyBatis 后仍必须手动拼接所有 JDBC 样板代码，Mapper 接口没有实际作用。",
    keywordPrompt: "在接口方法上直接编写查询 SQL 时常用的注解是 ____。",
    keyword: "@Select",
    scenario: "适合在用户、订单和成绩等模块中组织数据库读写操作。",
    codeLinePrompt: "补全根据 id 查询用户的 SQL 注解。",
    codeLine: "@Select(\"select * from user where id = #{id}\")",
    codeTask: "编写一个 UserMapper 接口，提供按 id 查询与新增用户的方法。",
  },
  {
    subject: "MySQL",
    knowledgePoint: "B+Tree 索引",
    term: "B+Tree 索引",
    definition: "用于提升基于索引列的查询效率，并支持范围查询等访问模式。",
    features: ["适合等值查询与范围查询", "索引有助于减少全表扫描", "建立过多索引会增加写入维护成本"],
    misconception: "任意 SQL 只要有索引就一定命中索引，与条件顺序和函数处理无关。",
    keywordPrompt: "在 MySQL 中为 name 列创建普通索引时使用的关键字是 ____。",
    keyword: "INDEX",
    scenario: "适合用户表按用户名查找、订单表按时间范围检索等业务。",
    codeLinePrompt: "补全创建索引的关键 SQL 片段。",
    codeLine: "create index idx_name on user(name);",
    codeTask: "分析用户表按 email 查询变慢的原因，并给出建立索引的 SQL 示例。",
  },
  {
    subject: "MySQL",
    knowledgePoint: "事务隔离级别",
    term: "事务隔离级别",
    definition: "用于平衡并发读取一致性与系统吞吐之间的关系。",
    features: ["常见级别包括读已提交与可重复读", "隔离级别越高通常并发开销越大", "理解脏读与不可重复读有助于正确选型"],
    misconception: "只要开启事务，就不会出现任何并发一致性问题，隔离级别配置没有意义。",
    keywordPrompt: "MySQL InnoDB 默认常见的隔离级别是 ____。",
    keyword: "REPEATABLE READ",
    scenario: "适合资金扣减、订单状态变更和报表查询等对一致性敏感的场景。",
    codeLinePrompt: "补全设置事务隔离级别的 SQL 片段。",
    codeLine: "set session transaction isolation level read committed;",
    codeTask: "说明脏读、不可重复读与幻读的区别，并给出隔离级别选择建议。",
  },
  {
    subject: "MySQL",
    knowledgePoint: "LEFT JOIN",
    term: "LEFT JOIN",
    definition: "用于以左表为基准关联右表，即使右表无匹配记录也保留左表数据。",
    features: ["左表记录会被优先保留", "适合主表配扩展表的查询", "右表无匹配时相关列通常为 null"],
    misconception: "LEFT JOIN 只会返回两张表都匹配成功的交集记录。",
    keywordPrompt: "以用户表为主，查询用户及其可选成绩时常用的关联方式是 ____。",
    keyword: "LEFT JOIN",
    scenario: "适合用户与订单、课程与选课记录等主从关系查询。",
    codeLinePrompt: "补全左连接的关键 SQL 片段。",
    codeLine: "from user u left join score s on u.id = s.user_id",
    codeTask: "编写一条 SQL，查询所有课程及其选课人数，没有选课记录的课程也要显示。",
  },
  {
    subject: "HTML5",
    knowledgePoint: "语义化标签",
    term: "语义化标签",
    definition: "用于通过更有语义的元素结构表达页面区域与内容角色。",
    features: ["有助于提升可读性与可维护性", "利于搜索引擎和辅助技术理解页面结构", "常见标签包括 header、main、article 等"],
    misconception: "页面只要能显示出来，所有区域都使用 div 才是最标准的 HTML5 写法。",
    keywordPrompt: "在页面中表示主要内容区域时，常用的语义化标签是 ____。",
    keyword: "main",
    scenario: "适合新闻页、博客页、后台首页等有明显结构分区的页面。",
    codeLinePrompt: "补全声明页头区域的标签。",
    codeLine: "<header></header>",
    codeTask: "编写一个文章详情页骨架，使用 header、main、article、aside、footer 组织结构。",
  },
  {
    subject: "HTML5",
    knowledgePoint: "表单校验",
    term: "表单原生校验",
    definition: "用于在浏览器层面对输入内容做必填、格式和范围等基础检查。",
    features: ["required 可约束必填项", "type=email 可做基础邮箱格式检查", "可结合 pattern 扩展规则"],
    misconception: "只要写了 required，后端就完全不需要再做任何数据校验。",
    keywordPrompt: "把输入框声明为邮箱类型时，input 的 type 值是 ____。",
    keyword: "email",
    scenario: "适合注册表单、登录表单和后台数据录入页面的基础校验。",
    codeLinePrompt: "补全必填输入框的关键属性。",
    codeLine: "<input required />",
    codeTask: "编写一个注册表单，包含邮箱、密码和确认密码，并结合原生校验属性进行限制。",
  },
  {
    subject: "CSS3",
    knowledgePoint: "Flexbox",
    term: "Flex 布局",
    definition: "用于在一维方向上高效完成子元素对齐、分配与伸缩。",
    features: ["支持主轴与交叉轴对齐", "适合导航栏、按钮组与卡片行布局", "常用属性包括 justify-content 与 align-items"],
    misconception: "Flex 只能让元素水平排列，无法实现纵向布局。",
    keywordPrompt: "把容器声明为 Flex 布局时，display 的值是 ____。",
    keyword: "flex",
    scenario: "适合页头导航、搜索栏、标签行和左右对齐操作区。",
    codeLinePrompt: "补全让子元素水平居中的关键样式。",
    codeLine: "justify-content: center;",
    codeTask: "编写一个顶部工具栏，左侧放标题，右侧放三个操作按钮，并使用 Flex 完成对齐。",
  },
  {
    subject: "CSS3",
    knowledgePoint: "Grid",
    term: "Grid 布局",
    definition: "用于在二维行列体系中组织页面区块，更适合复杂面板布局。",
    features: ["可以同时控制行与列", "适合仪表盘与卡片宫格", "常用属性包括 grid-template-columns"],
    misconception: "Grid 只能用于固定宽度页面，无法与响应式布局结合。",
    keywordPrompt: "定义三列等宽网格时，常用属性是 ____。",
    keyword: "grid-template-columns",
    scenario: "适合后台看板、图片墙和报表面板等二维布局场景。",
    codeLinePrompt: "补全把容器声明为 Grid 的关键样式。",
    codeLine: "display: grid;",
    codeTask: "编写一个三列卡片面板，桌面端三列展示，移动端自动降为单列。",
  },
  {
    subject: "CSS3",
    knowledgePoint: "选择器优先级",
    term: "CSS 优先级",
    definition: "用于决定多个样式规则命中同一元素时最终采用哪一条声明。",
    features: ["行内样式权重通常较高", "id 选择器优先级高于类选择器", "样式冲突时需结合来源与书写顺序一起判断"],
    misconception: "只要选择器写得更长，优先级就一定比任何 id 选择器更高。",
    keywordPrompt: "在常见选择器中，优先级通常高于类选择器的是 ____ 选择器。",
    keyword: "id",
    scenario: "适合排查组件样式被覆盖、主题样式冲突等问题。",
    codeLinePrompt: "补全类选择器的示例写法。",
    codeLine: ".card-title { color: #333; }",
    codeTask: "分析一个按钮同时命中标签、类和 id 样式时的最终颜色来源。",
  },
  {
    subject: "JavaScript",
    knowledgePoint: "Promise",
    term: "Promise",
    definition: "用于表示一个异步操作最终完成或失败的结果，并组织后续回调。",
    features: ["常见状态包括 pending、fulfilled、rejected", "支持 then 与 catch 链式处理", "适合封装异步请求流程"],
    misconception: "Promise 创建后会阻塞主线程，直到异步任务完成后才继续执行后面的同步代码。",
    keywordPrompt: "在 Promise 成功后处理结果时，常用的方法是 ____。",
    keyword: "then",
    scenario: "适合接口请求、文件读取和延迟任务等异步流程管理。",
    codeLinePrompt: "补全捕获 Promise 异常的关键代码。",
    codeLine: "promise.catch(handleError);",
    codeTask: "封装一个返回 Promise 的 delay 函数，在指定毫秒后 resolve。",
  },
  {
    subject: "JavaScript",
    knowledgePoint: "async/await",
    term: "async/await",
    definition: "用于以接近同步代码的写法组织 Promise 异步流程。",
    features: ["await 只能出现在 async 函数中", "有助于减少 then 嵌套", "通常与 try/catch 配合处理异常"],
    misconception: "使用 await 后，整个浏览器线程会被完全挂起，页面无法继续处理任何任务。",
    keywordPrompt: "在函数中使用 await 前，需要先声明函数为 ____。",
    keyword: "async",
    scenario: "适合按顺序请求多个接口、串行执行异步任务等场景。",
    codeLinePrompt: "补全等待 fetch 响应结果的关键代码。",
    codeLine: "const response = await fetch(url);",
    codeTask: "编写一个 async 函数，顺序请求用户信息和用户成绩，并在控制台输出结果。",
  },
  {
    subject: "JavaScript",
    knowledgePoint: "事件委托",
    term: "事件委托",
    definition: "用于把多个子元素的事件处理绑定到公共父元素上，减少重复监听器。",
    features: ["依赖事件冒泡机制", "适合动态列表项点击处理", "有助于减少大量节点分别绑定事件的成本"],
    misconception: "事件委托只能处理 input 的 change 事件，不能处理 click 等常见冒泡事件。",
    keywordPrompt: "事件委托常通过事件对象中的 ____ 判断真实触发元素。",
    keyword: "target",
    scenario: "适合菜单列表、表格按钮组和动态渲染卡片的点击处理。",
    codeLinePrompt: "补全通过事件对象获取真实触发节点的关键代码。",
    codeLine: "const target = event.target;",
    codeTask: "编写一个列表点击示例，只在 ul 上绑定一次事件，点击 li 时输出其 data-id。",
  },
  {
    subject: "JavaScript",
    knowledgePoint: "闭包",
    term: "闭包",
    definition: "用于让函数在离开定义环境后仍然访问其词法作用域中的变量。",
    features: ["可以封装私有状态", "常见于工厂函数与高阶函数", "使用不当可能让本应释放的数据继续被引用"],
    misconception: "闭包会自动清空外层变量，因此无法保存任何状态。",
    keywordPrompt: "闭包能够访问其定义时所在的 ____ 作用域。",
    keyword: "词法",
    scenario: "适合实现计数器、缓存函数和参数记忆化等逻辑。",
    codeLinePrompt: "补全返回内部函数的关键代码。",
    codeLine: "return function () { count += 1; return count; };",
    codeTask: "编写一个 createCounter 工厂函数，每次调用返回的函数都能累计自己的计数值。",
  },
  {
    subject: "JavaScript",
    knowledgePoint: "Fetch 与 CORS",
    term: "Fetch API",
    definition: "用于在浏览器中发起网络请求，并通过 Promise 获取响应结果。",
    features: ["默认返回 Promise", "可结合 headers、method 与 body 配置请求", "跨域请求通常需要服务端正确返回 CORS 响应头"],
    misconception: "只要前端使用 fetch 发起请求，浏览器就不会再检查跨域策略。",
    keywordPrompt: "把请求方式设置为 POST 时，fetch 配置项中常用的字段名是 ____。",
    keyword: "method",
    scenario: "适合前后端分离项目中的数据查询、提交和文件上传。",
    codeLinePrompt: "补全获取 JSON 响应体的关键代码。",
    codeLine: "const data = await response.json();",
    codeTask: "编写一个 fetch 请求示例，向 /api/login 发送 POST 请求并解析返回 JSON。",
  },
  {
    subject: "TypeScript",
    knowledgePoint: "interface",
    term: "interface",
    definition: "用于描述对象结构、函数签名或类契约，提高代码可读性与类型约束。",
    features: ["可声明属性与方法类型", "适合约束接口返回值结构", "能够帮助团队统一数据模型"],
    misconception: "interface 会在运行时生成真实对象，因此可以直接在浏览器里遍历 interface 实例。",
    keywordPrompt: "在 TypeScript 中声明对象结构时使用的关键字是 ____。",
    keyword: "interface",
    scenario: "适合定义用户信息、接口响应体和组件 props 结构。",
    codeLinePrompt: "补全声明用户对象结构的关键代码。",
    codeLine: "interface User { id: number; name: string; }",
    codeTask: "定义一个 ScoreRecord 接口，包含课程名、分数和是否及格三个字段。",
  },
  {
    subject: "TypeScript",
    knowledgePoint: "泛型",
    term: "TypeScript 泛型",
    definition: "用于在保持类型约束的同时编写可复用的函数、接口与组件。",
    features: ["可通过类型参数复用逻辑", "能够保留输入输出之间的类型关系", "适合通用工具函数与数据容器"],
    misconception: "泛型只适用于类定义，函数和接口中都不能使用类型参数。",
    keywordPrompt: "声明一个接收任意类型并原样返回的函数时，常见类型参数名称是 ____。",
    keyword: "T",
    scenario: "适合封装列表工具、请求结果包装器和通用表单组件。",
    codeLinePrompt: "补全泛型函数声明的关键代码。",
    codeLine: "function identity<T>(value: T): T { return value; }",
    codeTask: "编写一个泛型函数 firstItem，返回数组中的第一个元素并保留元素类型。",
  },
  {
    subject: "TypeScript",
    knowledgePoint: "联合类型收窄",
    term: "联合类型收窄",
    definition: "用于在多种可能类型之间通过条件判断缩小当前分支的实际类型。",
    features: ["常见方式包括 typeof、in 和自定义守卫", "有助于安全访问不同类型的特有属性", "适合处理接口响应中的多态数据"],
    misconception: "联合类型变量一旦声明，就不能再根据条件判断缩小其类型范围。",
    keywordPrompt: "在 TypeScript 中判断变量是否为字符串时，常用运算符是 ____。",
    keyword: "typeof",
    scenario: "适合处理 string | number、成功/失败响应模型等分支逻辑。",
    codeLinePrompt: "补全对字符串分支进行判断的关键代码。",
    codeLine: "if (typeof value === \"string\") { return value.toUpperCase(); }",
    codeTask: "编写一个函数，接收 string | number，并分别返回大写字符串或加一后的数字。",
  },
  {
    subject: "Vue 2",
    knowledgePoint: "computed",
    term: "computed 计算属性",
    definition: "用于基于已有响应式数据派生新值，并在依赖不变时缓存计算结果。",
    features: ["依赖变化时才会重新计算", "适合模板中复杂展示逻辑", "比在模板里写长表达式更易维护"],
    misconception: "computed 每次渲染都会无条件重新执行，因此和 methods 完全一样。",
    keywordPrompt: "在 Vue 2 中声明计算属性的配置项名称是 ____。",
    keyword: "computed",
    scenario: "适合购物车总价、筛选结果数量和格式化展示文本等场景。",
    codeLinePrompt: "补全声明总价计算属性的关键代码。",
    codeLine: "totalPrice() { return this.items.reduce((sum, item) => sum + item.price, 0); }",
    codeTask: "编写一个 Vue 2 组件，使用 computed 根据输入的姓和名拼接 fullName。",
  },
  {
    subject: "Vue 2",
    knowledgePoint: "watch",
    term: "watch 侦听器",
    definition: "用于在指定响应式数据变化时执行副作用逻辑。",
    features: ["适合在数据变化后发起请求", "可以监听单个字段或表达式", "常用于同步本地缓存或触发表单校验"],
    misconception: "watch 只能监听 props，不能监听 data 或计算结果。",
    keywordPrompt: "在 Vue 2 选项式 API 中声明侦听器的配置项名称是 ____。",
    keyword: "watch",
    scenario: "适合根据搜索关键词变化自动查询、根据开关变化写入本地存储。",
    codeLinePrompt: "补全监听 keyword 变化的方法名定义。",
    codeLine: "keyword(newValue) { this.loadList(newValue); }",
    codeTask: "编写一个 Vue 2 页面，监听分页参数变化后自动重新加载列表。",
  },
  {
    subject: "Vue 2",
    knowledgePoint: "生命周期",
    term: "Vue 2 生命周期",
    definition: "用于在组件创建、挂载、更新和销毁等阶段执行对应逻辑。",
    features: ["mounted 常用于发起首次数据请求", "beforeDestroy 可做清理操作", "理解生命周期有助于放置正确的副作用逻辑"],
    misconception: "Vue 组件只有 mounted 一个生命周期钩子，其余阶段都不会触发。",
    keywordPrompt: "Vue 2 组件首次渲染到页面后常进入的钩子是 ____。",
    keyword: "mounted",
    scenario: "适合在组件挂载后请求接口、绑定定时器和初始化第三方库。",
    codeLinePrompt: "补全在销毁前清理定时器的钩子名。",
    codeLine: "beforeDestroy() { clearInterval(this.timer); }",
    codeTask: "说明一个图表组件在 created、mounted 和 beforeDestroy 中分别适合做什么。",
  },
  {
    subject: "Vue 3",
    knowledgePoint: "ref",
    term: "ref",
    definition: "用于把基础类型或对象包装为带有响应式 value 的引用。",
    features: ["访问值时通常使用 .value", "适合保存数字、字符串等基础类型状态", "在模板中会自动解包显示"],
    misconception: "ref 只能存储 DOM 节点，不能保存普通状态值。",
    keywordPrompt: "在 Vue 3 中创建基础类型响应式值时，常用函数是 ____。",
    keyword: "ref",
    scenario: "适合计数器、输入框文本和加载状态等简单状态管理。",
    codeLinePrompt: "补全创建数字计数器的关键代码。",
    codeLine: "const count = ref(0);",
    codeTask: "编写一个 Vue 3 组件，使用 ref 管理关键字输入并在按钮点击时递增计数。",
  },
  {
    subject: "Vue 3",
    knowledgePoint: "reactive",
    term: "reactive",
    definition: "用于把对象包装为响应式代理，便于统一管理多字段状态。",
    features: ["更适合对象与嵌套结构状态", "返回的是代理对象", "常用于表单对象与筛选条件集合"],
    misconception: "reactive 返回的是原始对象本身，因此修改它不会触发视图更新。",
    keywordPrompt: "在 Vue 3 中把对象变成响应式代理时，常用函数是 ____。",
    keyword: "reactive",
    scenario: "适合复杂表单、筛选面板和多字段详情页状态。",
    codeLinePrompt: "补全创建响应式表单对象的关键代码。",
    codeLine: "const form = reactive({ name: \"\", age: 0 });",
    codeTask: "编写一个 Vue 3 登录表单示例，使用 reactive 管理 username 和 password。",
  },
  {
    subject: "Vue 3",
    knowledgePoint: "Composition API",
    term: "Composition API",
    definition: "用于按逻辑关注点组织组件代码，提高复用性与可维护性。",
    features: ["常与 setup 组合使用", "便于抽离复用逻辑到 composable", "适合复杂组件逻辑拆分"],
    misconception: "Composition API 只能在类组件中使用，和函数式组织方式无关。",
    keywordPrompt: "在 Vue 3 单文件组件中承载组合式逻辑的常见入口是 ____。",
    keyword: "setup",
    scenario: "适合把分页、搜索、权限和图表等逻辑拆分为可复用模块。",
    codeLinePrompt: "补全在 script setup 中声明响应式数据的关键代码。",
    codeLine: "const keyword = ref(\"\");",
    codeTask: "编写一个 usePagination composable，暴露 page、pageSize 和 nextPage 方法。",
  },
  {
    subject: "Vue 3",
    knowledgePoint: "watchEffect",
    term: "watchEffect",
    definition: "用于自动收集副作用中访问到的响应式依赖，并在依赖变化时重新执行。",
    features: ["不需要手动指定依赖源", "适合快速响应多个依赖的副作用逻辑", "在副作用里读取到哪些响应式值就会跟踪哪些值"],
    misconception: "watchEffect 只会在组件创建时执行一次，后续依赖变化不会再次触发。",
    keywordPrompt: "在 Vue 3 中自动跟踪副作用依赖时，常用函数是 ____。",
    keyword: "watchEffect",
    scenario: "适合根据多个筛选条件变化自动请求数据或刷新预览。",
    codeLinePrompt: "补全在副作用中打印 keyword 的关键代码。",
    codeLine: "watchEffect(() => console.log(keyword.value));",
    codeTask: "编写一个示例，使用 watchEffect 在 page 或 keyword 变化时自动调用 loadList。",
  },
  {
    subject: "Node.js",
    knowledgePoint: "Event Loop",
    term: "事件循环",
    definition: "用于协调同步代码、宏任务与微任务的执行顺序，是 Node.js 异步模型的核心。",
    features: ["同步代码先执行", "微任务通常会在当前阶段结束前尽快清空", "理解事件循环有助于分析输出顺序"],
    misconception: "setTimeout(fn, 0) 一定会在当前同步代码之前立即执行。",
    keywordPrompt: "把回调压入微任务队列时，常见方法之一是 ____。",
    keyword: "Promise.resolve().then",
    scenario: "适合分析日志输出顺序、异步回调时机和任务调度问题。",
    codeLinePrompt: "补全创建一个微任务的关键代码。",
    codeLine: "Promise.resolve().then(() => console.log(\"microtask\"));",
    codeTask: "给出一个同时包含同步代码、Promise.then 和 setTimeout 的示例，并说明最终输出顺序。",
  },
  {
    subject: "Node.js",
    knowledgePoint: "Express 中间件",
    term: "Express 中间件",
    definition: "用于在请求处理链中分层组织通用逻辑，如鉴权、日志、解析与异常处理。",
    features: ["可以通过 next 继续传递请求", "适合统一日志和权限校验", "可按路径或全局注册"],
    misconception: "Express 中间件执行后不能再交给后续处理器，因此不能形成链式调用。",
    keywordPrompt: "Express 中把请求交给下一个中间件时常用的方法是 ____。",
    keyword: "next",
    scenario: "适合在接口层做 token 校验、耗时统计和统一错误处理。",
    codeLinePrompt: "补全继续执行后续中间件的关键代码。",
    codeLine: "next();",
    codeTask: "编写一个 Express 中间件，记录请求方法与路径，再交给后续路由处理。",
  },
  {
    subject: "ECharts",
    knowledgePoint: "option",
    term: "option 配置对象",
    definition: "用于集中描述图表标题、提示框、坐标轴、图例与系列等配置。",
    features: ["通常包含 series 数组", "可以同时配置 tooltip 与 legend", "适合把图表结构与样式集中组织在一个对象里"],
    misconception: "ECharts 只能通过单独 API 设置 series，不能在一个 option 对象里统一声明其他配置。",
    keywordPrompt: "把配置项真正应用到图表实例上的方法是 ____。",
    keyword: "setOption",
    scenario: "适合在接口数据返回后一次性组织图表结构，并按需更新数据系列。",
    codeLinePrompt: "补全将 option 应用到 chart 实例的关键代码。",
    codeLine: "chart.setOption(option);",
    codeTask: "编写一个最小示例，使用 ECharts 渲染包含 title、tooltip 和单个 bar 系列的柱状图。",
  },
];

function readStore() {
  return JSON.parse(fs.readFileSync(storeFile, "utf8"));
}

function writeStore(store) {
  fs.writeFileSync(storeFile, JSON.stringify(store, null, 2), "utf8");
}

function rotate(list, offset) {
  const result = [...list];
  const normalized = ((offset % result.length) + result.length) % result.length;
  return result.slice(normalized).concat(result.slice(0, normalized));
}

function buildChoiceOptions(definitions, index) {
  const correct = definitions[index];
  const options = [correct];
  for (let step = 7; options.length < 4; step += 11) {
    const candidate = definitions[(index + step) % definitions.length];
    if (!options.includes(candidate)) {
      options.push(candidate);
    }
  }
  return rotate(options, index % 4);
}

function buildQuestion(id, teacherId, concept, type, title, answer, options = []) {
  return {
    id,
    teacherId,
    subject: concept.subject,
    knowledgePoint: concept.knowledgePoint,
    difficulty: DIFFICULTY_BY_TYPE[type],
    type,
    title,
    options,
    answer: Array.isArray(answer) ? answer : [answer],
    score: SCORE_BY_TYPE[type],
    sourceTag: SOURCE_TAG,
  };
}

function buildQuestions(teacherId) {
  const definitions = concepts.map((item) => item.definition);
  const questions = [];
  let counter = 1;

  concepts.forEach((concept, index) => {
    const nextId = () => `question-bank-${String(counter++).padStart(4, "0")}`;
    const singleOptions = buildChoiceOptions(definitions, index);
    const multipleOptions = rotate([...concept.features, concept.misconception], index % 4);

    questions.push(
      buildQuestion(nextId(), teacherId, concept, "single", `关于 ${concept.term} 的描述，哪一项最准确？`, singleOptions[0], singleOptions),
      buildQuestion(nextId(), teacherId, concept, "multiple", `关于 ${concept.term} 的特点，下列哪些说法正确？`, concept.features, multipleOptions),
      buildQuestion(nextId(), teacherId, concept, "judge", `${concept.term} 相关判断：${concept.features[0]}。`, "正确", ["正确", "错误"]),
      buildQuestion(nextId(), teacherId, concept, "judge", `${concept.term} 相关判断：${concept.misconception}`, "错误", ["正确", "错误"]),
      buildQuestion(nextId(), teacherId, concept, "fill", `请填空：在 ${concept.subject} 中，${concept.definition} 对应的关键概念通常是 ____。`, concept.term),
      buildQuestion(nextId(), teacherId, concept, "fill", `编程填空：${concept.keywordPrompt}`, concept.keyword),
      buildQuestion(nextId(), teacherId, concept, "short", `简述 ${concept.term} 的核心作用。`, `${concept.definition}${concept.features.join("；")}。`),
      buildQuestion(nextId(), teacherId, concept, "short", `说明 ${concept.term} 更适合的使用场景。`, concept.scenario),
      buildQuestion(nextId(), teacherId, concept, "coding", `编程填空：${concept.codeLinePrompt}`, concept.codeLine),
      buildQuestion(
        nextId(),
        teacherId,
        concept,
        "coding",
        `编程题：${concept.codeTask}`,
        `参考实现应围绕 ${concept.term} 展开，核心代码可包含：${concept.codeLine} 并结合场景完成输入、处理与返回。`
      )
    );
  });

  return questions;
}

function pickQuestionsForPaper(bucket, paperIndex, count) {
  return Array.from({ length: count }, (_, offset) => bucket[paperIndex + offset * TOTAL_PAPERS]);
}

function buildPapers(teacherId, questions) {
  const buckets = {
    single: questions.filter((item) => item.type === "single"),
    multiple: questions.filter((item) => item.type === "multiple"),
    judge: questions.filter((item) => item.type === "judge"),
    fill: questions.filter((item) => item.type === "fill"),
    short: questions.filter((item) => item.type === "short"),
    coding: questions.filter((item) => item.type === "coding"),
  };

  return Array.from({ length: TOTAL_PAPERS }, (_, paperIndex) => {
    const selected = [
      ...pickQuestionsForPaper(buckets.single, paperIndex, PAPER_BLUEPRINT.single),
      ...pickQuestionsForPaper(buckets.multiple, paperIndex, PAPER_BLUEPRINT.multiple),
      ...pickQuestionsForPaper(buckets.judge, paperIndex, PAPER_BLUEPRINT.judge),
      ...pickQuestionsForPaper(buckets.fill, paperIndex, PAPER_BLUEPRINT.fill),
      ...pickQuestionsForPaper(buckets.short, paperIndex, PAPER_BLUEPRINT.short),
      ...pickQuestionsForPaper(buckets.coding, paperIndex, PAPER_BLUEPRINT.coding),
    ];
    const totalScore = selected.reduce((sum, item) => sum + Number(item.score || 0), 0);
    return {
      id: `paper-comprehensive-${String(paperIndex + 1).padStart(2, "0")}`,
      teacherId,
      name: `综合试卷 ${String(paperIndex + 1).padStart(2, "0")}`,
      durationMinutes: 120,
      totalScore,
      passScore: Math.ceil(totalScore * 0.6),
      questionIds: selected.map((item) => item.id),
      paperType: "comprehensive",
      sourceTag: PAPER_TAG,
    };
  });
}

function removeGeneratedRecords(store) {
  const generatedQuestionIds = new Set(
    (store.questions || []).filter((item) => item.sourceTag === SOURCE_TAG).map((item) => item.id)
  );

  store.questions = (store.questions || []).filter((item) => item.sourceTag !== SOURCE_TAG);
  store.papers = (store.papers || []).filter((item) => item.sourceTag !== PAPER_TAG);
  store.submissions = (store.submissions || []).map((submission) => ({
    ...submission,
    answers: (submission.answers || []).filter((item) => !generatedQuestionIds.has(item.questionId)),
    answerDetail: (submission.answerDetail || []).filter((item) => !generatedQuestionIds.has(item.questionId)),
  }));
  store.wrongBookEntries = (store.wrongBookEntries || []).filter((item) => !generatedQuestionIds.has(item.questionId));
}

function dropUnusedOverlappingPapers(store) {
  const referencedPaperIds = new Set((store.exams || []).map((item) => item.paperId));
  const prioritized = [...(store.papers || [])].sort((left, right) => {
    const leftReferenced = referencedPaperIds.has(left.id) ? 0 : 1;
    const rightReferenced = referencedPaperIds.has(right.id) ? 0 : 1;
    return leftReferenced - rightReferenced;
  });

  const usedQuestionIds = new Set();
  const keptPapers = [];

  prioritized.forEach((paper) => {
    const overlaps = (paper.questionIds || []).some((questionId) => usedQuestionIds.has(questionId));
    if (overlaps && !referencedPaperIds.has(paper.id)) {
      return;
    }
    keptPapers.push(paper);
    (paper.questionIds || []).forEach((questionId) => usedQuestionIds.add(questionId));
  });

  store.papers = keptPapers;
}

function main() {
  const store = readStore();
  store.questions = Array.isArray(store.questions) ? store.questions : [];
  store.papers = Array.isArray(store.papers) ? store.papers : [];
  store.exams = Array.isArray(store.exams) ? store.exams : [];
  store.submissions = Array.isArray(store.submissions) ? store.submissions : [];
  store.wrongBookEntries = Array.isArray(store.wrongBookEntries) ? store.wrongBookEntries : [];
  store.logs = Array.isArray(store.logs) ? store.logs : [];

  removeGeneratedRecords(store);
  dropUnusedOverlappingPapers(store);

  const teacherId = store.users.find((item) => item.role === "teacher")?.id || "teacher-1";
  const questions = buildQuestions(teacherId);
  const papers = buildPapers(teacherId, questions);

  store.questions.unshift(...questions);
  store.papers.unshift(...papers);
  store.logs.unshift({
    id: `log-seed-${Date.now()}`,
    actorId: teacherId,
    action: "批量生成题库与综合试卷",
    detail: JSON.stringify({ questionCount: questions.length, paperCount: papers.length, sourceTag: SOURCE_TAG }),
    time: new Date().toISOString(),
  });

  writeStore(store);

  console.log(`Generated ${questions.length} questions and ${papers.length} papers.`);
  console.log(`Question bank total: ${store.questions.length}`);
  console.log(`Paper total: ${store.papers.length}`);
}

main();
