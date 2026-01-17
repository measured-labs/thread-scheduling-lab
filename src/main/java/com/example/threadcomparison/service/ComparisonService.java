package com.example.threadcomparison.service;

import com.example.threadcomparison.model.ComparisonResult;
import com.example.threadcomparison.model.SamplingResult;
import com.example.threadcomparison.sampler.ManualThreadSampler;
import com.example.threadcomparison.sampler.ScheduledThreadSampler;
import org.springframework.stereotype.Service;

@Service
public class ComparisonService {

    private final ManualThreadSampler manualSampler;
    private final ScheduledThreadSampler scheduledSampler;

    public ComparisonService(ManualThreadSampler manualSampler, 
                            ScheduledThreadSampler scheduledSampler) {
        this.manualSampler = manualSampler;
        this.scheduledSampler = scheduledSampler;
    }

    public ComparisonResult runComparison(long intervalMs, long durationMs) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("스레드 샘플링 방식 비교 테스트");
        System.out.printf("설정: 간격=%dms, 시간=%dms%n", intervalMs, durationMs);
        System.out.println("=".repeat(60));

        // 1. Manual Thread 테스트
        System.out.println("\n[1/2] Manual Thread + Sleep 테스트 중...");
        SamplingResult manualResult = manualSampler.runSampling(intervalMs, durationMs);
        printResult(manualResult);

        // 잠시 대기 (안정화)
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 2. Scheduled Thread 테스트
        System.out.println("\n[2/2] ScheduledExecutorService 테스트 중...");
        SamplingResult scheduledResult = scheduledSampler.runSampling(intervalMs, durationMs);
        printResult(scheduledResult);

        // 승자 결정
        String winner = determineWinner(manualResult, scheduledResult);
        String summary = buildSummary(manualResult, scheduledResult, intervalMs);

        System.out.println("\n" + "=".repeat(60));
        System.out.println("결과: " + winner);
        System.out.println(summary);
        System.out.println("=".repeat(60) + "\n");

        return new ComparisonResult(manualResult, scheduledResult, winner, summary);
    }

    private void printResult(SamplingResult result) {
        System.out.printf("""
              방식: %s
              샘플 수: %d (예상: %d)
              정확도: %.1f%%
              평균 간격: %.2fms
              간격 범위: %dms ~ %dms
              표준편차: %.2fms (낮을수록 일정)
            """,
            result.method(),
            result.totalSamples(),
            result.expectedSamples(),
            result.accuracy(),
            result.avgInterval(),
            result.minInterval(),
            result.maxInterval(),
            result.stdDeviation()
        );
    }

    private String determineWinner(SamplingResult manual, SamplingResult scheduled) {
        // 점수 계산 (표준편차 + 정확도)
        double manualScore = manual.stdDeviation() + Math.max(0, 100 - manual.accuracy());
        double scheduledScore = scheduled.stdDeviation() + Math.max(0, 100 - scheduled.accuracy());

        if (scheduledScore < manualScore) {
            return "ScheduledExecutorService 승리! 🏆";
        } else if (manualScore < scheduledScore) {
            return "Manual Thread 승리! 🏆";
        } else {
            return "무승부";
        }
    }

    private String buildSummary(SamplingResult manual, SamplingResult scheduled, long targetInterval) {
        double manualDrift = manual.avgInterval() - targetInterval;
        double scheduledDrift = scheduled.avgInterval() - targetInterval;

        return String.format("""
            
            📊 상세 비교:
            ┌─────────────────────┬──────────────────┬──────────────────┐
            │ 항목                │ Manual Thread    │ Scheduled        │
            ├─────────────────────┼──────────────────┼──────────────────┤
            │ 총 샘플 수          │ %16d │ %16d │
            │ 목표 대비 정확도    │ %15.1f%% │ %15.1f%% │
            │ 평균 간격 (목표:%dms) │ %14.1fms │ %14.1fms │
            │ 간격 드리프트       │ %+15.1fms │ %+15.1fms │
            │ 표준편차 (일정함)   │ %14.2fms │ %14.2fms │
            └─────────────────────┴──────────────────┴──────────────────┘
            
            💡 해석:
            • 간격 드리프트: 양수면 예상보다 느림 (작업 시간 누적)
            • 표준편차: 낮을수록 간격이 일정함
            • Manual은 sleep 전 작업시간이 누적되어 드리프트 발생
            • Scheduled는 작업시간을 보정하여 주기 유지 시도
            """,
            manual.totalSamples(), scheduled.totalSamples(),
            manual.accuracy(), scheduled.accuracy(),
            targetInterval,
            manual.avgInterval(), scheduled.avgInterval(),
            manualDrift, scheduledDrift,
            manual.stdDeviation(), scheduled.stdDeviation()
        );
    }
}
