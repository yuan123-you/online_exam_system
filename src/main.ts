import { createApp } from "vue";
import { createPinia } from "pinia";
import App from "./App.vue";
import "./styles/theme.css";
import "./styles/breakpoints.css";

async function bootstrap() {
  const app = createApp(App);
  const pinia = createPinia();
  app.use(pinia);

  // 先初始化认证状态（从 localStorage 恢复 token 并加载 bootstrap）
  // 必须在路由挂载之前完成，否则路由守卫首次触发时 bootstrap 还是 null
  const { useAppStore } = await import("@/stores/app");
  const store = useAppStore();
  await store.initAuth();

  // 认证就绪后再动态导入路由并挂载
  const { default: router } = await import("./router");
  app.use(router);
  app.mount("#app");
}

bootstrap();
