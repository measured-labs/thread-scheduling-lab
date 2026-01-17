package com.example.threadcomparison.controller;

import com.example.threadcomparison.model.ComparisonResult;
import com.example.threadcomparison.service.ComparisonService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ComparisonController {

    private final ComparisonService comparisonService;

    public ComparisonController(ComparisonService comparisonService) {
        this.comparisonService = comparisonService;
    }

    /**
     * 두 방식 비교 테스트 실행
     * GET /api/compare?interval=100&duration=5000
     */
    @GetMapping("/compare")
    public ComparisonResult compare(
            @RequestParam(defaultValue = "100") long interval,
            @RequestParam(defaultValue = "5000") long duration) {
        
        long safeInterval = Math.min(Math.max(interval, 10), 1000);   // 10ms ~ 1000ms
        long safeDuration = Math.min(Math.max(duration, 1000), 30000); // 1초 ~ 30초
        
        return comparisonService.runComparison(safeInterval, safeDuration);
    }

    /**
     * 빠른 테스트 (3초, 50ms 간격)
     */
    @GetMapping("/quick-test")
    public ComparisonResult quickTest() {
        return comparisonService.runComparison(50, 3000);
    }

    /**
     * 상세 테스트 (10초, 100ms 간격)
     */
    @GetMapping("/detailed-test")
    public ComparisonResult detailedTest() {
        return comparisonService.runComparison(100, 10000);
    }

    /**
     * 사용법 안내
     */
    @GetMapping("/")
    public Map<String, Object> index() {
        return Map.of(
            "title", "Thread Sampling 방식 비교 API",
            "description", "Manual Thread + Sleep vs ScheduledExecutorService",
            "endpoints", List.of(
                Map.of(
                    "path", "GET /api/compare",
                    "description", "커스텀 설정으로 비교 테스트",
                    "params", "interval (10-1000ms), duration (1000-30000ms)"
                ),
                Map.of(
                    "path", "GET /api/quick-test",
                    "description", "빠른 테스트 (3초, 50ms 간격)"
                ),
                Map.of(
                    "path", "GET /api/detailed-test",
                    "description", "상세 테스트 (10초, 100ms 간격)"
                )
            ),
            "비교항목", List.of(
                "샘플 수 정확도",
                "간격 일정함 (표준편차)",
                "드리프트 (목표 간격 대비 실제 간격)"
            )
        );
    }
}
