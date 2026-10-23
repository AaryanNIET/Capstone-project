package com.capstone.agenticrag.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

    /** Bounded pool for document ingestion so a burst of uploads cannot exhaust CPU or the embedding API quota. */
    @Bean(name = "ingestionExecutor")
    ThreadPoolTaskExecutor ingestionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("ingest-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /** Virtual threads for agent runs (blocking I/O on model and tool calls) and post-processing work. */
    @Bean(name = "agentExecutor", destroyMethod = "shutdown")
    ExecutorService agentExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("agent-", 0).factory());
    }
}
