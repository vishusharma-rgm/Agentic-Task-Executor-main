package com.agent.executor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync  // Phase 2: enables @Async for async tool execution
public class AgenticTaskExecutorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgenticTaskExecutorApplication.class, args);
        System.out.println("""
                
                ╔══════════════════════════════════════╗
                ║   Agentic Task Executor — RUNNING     ║
                ║   POST /execute  → start a task       ║
                ║   GET  /status/{id} → check progress  ║
                ╚══════════════════════════════════════╝
                """);
    }
}
