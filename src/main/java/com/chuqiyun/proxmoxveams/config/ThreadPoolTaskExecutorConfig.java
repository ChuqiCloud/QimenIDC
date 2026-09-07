package com.chuqiyun.proxmoxveams.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.TaskScheduler;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.RejectedExecutionHandler;

/**
 * @author mryunqi
 * @date 2023/3/9
 */
@Configuration
@EnableAsync
@Slf4j
public class ThreadPoolTaskExecutorConfig {

    @Bean(name = "taskExecutor")
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        //设置核心线程数
        executor.setCorePoolSize(20);
        //设置最大线程数
        executor.setMaxPoolSize(40);
        //缓冲队列200：用来缓冲执行任务的队列
        executor.setQueueCapacity(200);
        //线程活路时间 60 秒
        executor.setKeepAliveSeconds(60);
        //线程池名的前缀：设置好了之后可以方便我们定位处理任务所在的线程池
        executor.setThreadNamePrefix("taskExecutor-");
        //设置拒绝策略
        // Never run a long-running cron task on the scheduler thread when the pool is full.
        executor.setRejectedExecutionHandler(rejected("taskExecutor"));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        return executor;
    }

    @Bean(name = "vmFirewallSyncExecutor")
    public TaskExecutor vmFirewallSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 防火墙白名单全量同步属于低优先级后台任务，单线程慢速执行，避免影响业务线程池
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("vmFirewallSync-");
        executor.setRejectedExecutionHandler(rejected("vmFirewallSyncExecutor"));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        return executor;
    }

    @Bean(name = "taskScheduler")
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("scheduler-");
        scheduler.setRejectedExecutionHandler(rejected("taskScheduler"));
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        return scheduler;
    }

    @Bean(name = "createVmExecutor")
    public TaskExecutor createVmExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Creation waits for several child tasks; isolate its worker from those child-task executors.
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(0);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("createVm-");
        executor.setRejectedExecutionHandler(rejected("createVmExecutor"));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        return executor;
    }

    @Bean(name = "workflowExecutor")
    public TaskExecutor workflowExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Isolate workflows that wait on multiple PVE operations from short cron scans.
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(8);
        // A bounded queue prevents short scheduling bursts from being rejected while
        // long-running reinstall or migration workflows occupy all workers.
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("workflow-");
        executor.setRejectedExecutionHandler(rejected("workflowExecutor"));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        return executor;
    }

    @Bean(name = "deleteVmExecutor")
    public TaskExecutor deleteVmExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("deleteVm-");
        executor.setRejectedExecutionHandler(rejected("deleteVmExecutor"));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        return executor;
    }

    @Bean(name = "deleteRecycleExecutor")
    public TaskExecutor deleteRecycleExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("deleteRecycle-");
        executor.setRejectedExecutionHandler(rejected("deleteRecycleExecutor"));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        return executor;
    }

    private RejectedExecutionHandler rejected(String executorName) {
        return (runnable, executor) -> {
            log.error("异步任务提交被拒绝: executor={}, active={}, pool={}, queue={}",
                    executorName, executor.getActiveCount(), executor.getPoolSize(), executor.getQueue().size());
            throw new java.util.concurrent.RejectedExecutionException("Task rejected by " + executorName);
        };
    }
}
