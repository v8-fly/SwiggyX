package com.swiggyx.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ThreadPoolConfig {

    // Number of CPU cores on this machine
    private static final int CPU_CORES =
            Runtime.getRuntime().availableProcessors();

    // IO Thread Pool — cores × 2
    // threads mostly waiting for IO, can have more than cores
    @Bean(name = "ioThreadPool")
    public ExecutorService ioThreadPool() {
        System.out.println("Creating IO Thread Pool with "
                + (CPU_CORES * 2) + " threads");
        return Executors.newFixedThreadPool(CPU_CORES * 2);
    }

    // CPU Thread Pool — exactly num of cores
    // heavy calculation, cache stays hot, no over-switching
    @Bean(name = "cpuThreadPool")
    public ExecutorService cpuThreadPool() {
        System.out.println("Creating CPU Thread Pool with "
                + CPU_CORES + " threads");
        return Executors.newFixedThreadPool(CPU_CORES);
    }

}