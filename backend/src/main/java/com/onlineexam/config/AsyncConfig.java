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
 *
 * 线程池调优说明：
 * - 核心线程数与 concurrent-limit 对齐，确保信号量获取后有线程可用
 * - 最大线程数为核心的4倍，应对突发流量
 * - 队列容量充裕，避免 CallerRunsPolicy 频繁触发阻塞请求线程
 * - CallerRunsPolicy：队列满时由调用线程执行，提供背压而非丢弃任务
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

  @Value("${ai.thread-pool.core-size:8}")
  private int coreSize;

  @Value("${ai.thread-pool.max-size:32}")
  private int maxSize;

  @Value("${ai.thread-pool.queue-capacity:200}")
  private int queueCapacity;

  @Value("${ai.thread-pool.keep-alive-seconds:60}")
  private int keepAliveSeconds;

  /**
   * AI 操作专用线程池
   * - 核心线程 8：与 concurrent-limit 对齐，保证信号量获取后有线程执行
   * - 最大线程 32：应对突发流量，避免任务排队过长
   * - 队列容量 200：缓冲等待中的任务，减少 CallerRunsPolicy 触发
   * - CallerRunsPolicy：队列满时由调用线程执行，提供背压而非丢弃任务
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
