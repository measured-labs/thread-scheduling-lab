package com.example.threadcomparison.sampler;

import com.example.threadcomparison.model.SamplingResult;
import com.sun.management.OperatingSystemMXBean;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

/**
 * ScheduledExecutorService 방식
 * 
 * 특징:
 * - scheduleAtFixedRate()로 정확한 주기 보장
 * - 작업 시간이 있어도 주기 유지 시도
 * - 예외 발생해도 스케줄링 계속
 * - 스레드 풀 재사용
 */
@Component
public class ScheduledThreadSampler {

    private final OperatingSystemMXBean osMXBean = 
        (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    // 스레드 풀 (재사용)
    private final ScheduledExecutorService scheduler = 
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "scheduled-sampling-thread");
            t.setDaemon(true);
            return t;
        });

    private ScheduledFuture<?> samplingTask;

    private final List<Long> timestamps = new CopyOnWriteArrayList<>();
    private final List<Double> cpuSamples = new CopyOnWriteArrayList<>();

    public SamplingResult runSampling(long intervalMs, long durationMs) {
        // 초기화
        timestamps.clear();
        cpuSamples.clear();

        long startTime = System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(1);

        // 정확한 주기로 실행
        samplingTask = scheduler.scheduleAtFixedRate(() -> {
            long currentTime = System.currentTimeMillis();

            // 시간 초과 체크
            if (currentTime - startTime >= durationMs) {
                samplingTask.cancel(false);
                latch.countDown();
                return;
            }

            timestamps.add(currentTime);

            // CPU 샘플링
            double cpuLoad = osMXBean.getCpuLoad();
            if (cpuLoad >= 0) {
                cpuSamples.add(cpuLoad * 100);
            }

            // 의도적으로 약간의 작업 추가
            simulateWork();

        }, 0, intervalMs, TimeUnit.MILLISECONDS);

        // 완료 대기
        try {
            latch.await(durationMs + 1000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long actualDuration = timestamps.isEmpty() ? 0 : 
            timestamps.getLast() - startTime;

        return buildResult("ScheduledExecutorService", intervalMs, actualDuration);
    }

    private void simulateWork() {
        // 1~5ms 정도의 랜덤 작업 시뮬레이션
        double sum = 0.0;
        int iterations = ThreadLocalRandom.current().nextInt(1000, 5000);
        for (int i = 0; i < iterations; i++) {
            sum += Math.random();
        }
    }

    private SamplingResult buildResult(String method, long intervalMs, long durationMs) {
        List<Long> intervals = new ArrayList<>();
        for (int i = 1; i < timestamps.size(); i++) {
            intervals.add(timestamps.get(i) - timestamps.get(i - 1));
        }

        double avgInterval = intervals.isEmpty() ? 0 : 
            intervals.stream().mapToLong(Long::longValue).average().orElse(0);
        long minInterval = intervals.isEmpty() ? 0 : Collections.min(intervals);
        long maxInterval = intervals.isEmpty() ? 0 : Collections.max(intervals);

        // 표준편차 계산
        double stdDev = 0;
        if (intervals.size() > 1) {
            double finalAvg = avgInterval;
            double variance = intervals.stream()
                .mapToDouble(i -> Math.pow(i - finalAvg, 2))
                .average()
                .orElse(0);
            stdDev = Math.sqrt(variance);
        }

        int expectedSamples = (int) (durationMs / intervalMs);
        double accuracy = expectedSamples > 0 ? 
            ((double) timestamps.size() / expectedSamples) * 100 : 0;

        double avgCpu = cpuSamples.isEmpty() ? 0 : 
            cpuSamples.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double peakCpu = cpuSamples.isEmpty() ? 0 : Collections.max(cpuSamples);

        return new SamplingResult(
            method,
            timestamps.size(),
            durationMs,
            expectedSamples,
            accuracy,
            avgInterval,
            minInterval,
            maxInterval,
            stdDev,
            avgCpu,
            peakCpu
        );
    }

    public void stop() {
        if (samplingTask != null) {
            samplingTask.cancel(true);
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
