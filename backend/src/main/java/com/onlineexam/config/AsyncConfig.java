package com.onlineexam.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 异步任务与线程池配置
 * - 为 AI 流式请求、后台评分、题目导入等提供受管理的线程池
 * - 启用 @Async 和 @Scheduled 支持
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

  @Value("${ai.thread-pool.core-size:4}")
  private int coreSize;

  @Value("${ai.thread-pool.max-size:16}")
  private int maxSize;

  @Value("${ai.thread-pool.queue-capacity:100}")
  private int queueCapacity;

  @Value("${ai.thread-pool.keep-alive-seconds:60}")
  private int keepAliveSeconds;

  /**
   * AI 操作专用线程池
   * - 核心线程 4：满足日常并发需求
   * - 最大线程 16：应对突发流量
   * - 队列容量 100：缓冲等待中的任务
   * - CallerRunsPolicy：队列满时由调用线程执行，防止任务丢失
   */
  @Bean("aiTaskExecutor")
  public Executor aiTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(coreSize);
    executor.setMaxPoolSize(maxSize);
    executor.setQueueCapacity(queueCapacity);
    executor.setKeepAliveSeconds(keepAliveSeconds);
    executor.setThreadNamePrefix("ai-task-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    executor.initialize();
    return executor;
  }
}
