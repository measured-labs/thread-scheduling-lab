package com.example.threadcomparison.sampler;

import com.example.threadcomparison.model.SamplingResult;
import com.sun.management.OperatingSystemMXBean;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 수동 Thread + Sleep 방식
 * 
 * 특징:
 * - Thread.sleep()으로 간격 제어
 * - 작업 시간만큼 주기가 밀림
 * - 직접 while 루프 관리 필요
 */
@Component
public class ManualThreadSampler {

    private final OperatingSystemMXBean osMXBean = 
        (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    private Thread samplingThread;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    private final List<Long> timestamps = new CopyOnWriteArrayList<>();
    private final List<Double> cpuSamples = new CopyOnWriteArrayList<>();

    public SamplingResult runSampling(long intervalMs, long durationMs) {
        // 초기화
        timestamps.clear();
        cpuSamples.clear();
        isRunning.set(true);

        long startTime = System.currentTimeMillis();
        long endTime = startTime + durationMs;

        // 수동 Thread 생성
        samplingThread = new Thread(() -> {
            while (isRunning.get() && System.currentTimeMillis() < endTime) {
                long sampleTime = System.currentTimeMillis();
                timestamps.add(sampleTime);

                // CPU 샘플링
                double cpuLoad = osMXBean.getCpuLoad();
                if (cpuLoad >= 0) {
                    cpuSamples.add(cpuLoad * 100);
                }

                // 의도적으로 약간의 작업 추가 (실제 상황 시뮬레이션)
                simulateWork();

                try {
                    Thread.sleep(intervalMs);  // 고정 sleep
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "manual-sampling-thread");

        samplingThread.start();
        try {
            samplingThread.join();  // 완료 대기
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long actualDuration = timestamps.isEmpty() ? 0 : 
            timestamps.getLast() - startTime;

        return buildResult("Manual Thread + Sleep", intervalMs, actualDuration);
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
        isRunning.set(false);
        if (samplingThread != null) {
            samplingThread.interrupt();
        }
    }
}
