# Thread Sampling 방식 비교 프로젝트

Java 21 + Spring Boot 3.5.8 기반

## 개요

**Manual Thread + Sleep** vs **ScheduledExecutorService** 두 방식의 주기적 작업 실행을 비교합니다.

## 비교 항목

| 항목 | Manual Thread + Sleep | ScheduledExecutorService |
|------|----------------------|--------------------------|
| 주기 정확도 | 작업 시간만큼 밀림 | 정확한 주기 유지 시도 |
| 예외 처리 | 스레드가 죽을 수 있음 | 예외 발생해도 계속 실행 |
| 종료 처리 | interrupt + join 필요 | cancel() 한 줄 |
| 코드 복잡도 | while 루프 직접 관리 | 프레임워크가 관리 |
| 리소스 관리 | 매번 새 스레드 생성 | 스레드 풀 재사용 |

## 실행 방법

```bash
./gradlew bootRun
```

## API 엔드포인트

```bash
# 기본 비교 테스트
GET /api/compare?interval=100&duration=5000

# 빠른 테스트 (3초)
GET /api/quick-test

# 상세 테스트 (10초)
GET /api/detailed-test
```

## 핵심 코드 비교

### Manual Thread + Sleep
```java
new Thread(() -> {
        while (isRunning.get()) {
        timestamps.add(System.currentTimeMillis());
doWork();  // 작업 시간 발생
        Thread.sleep(intervalMs);  // 작업 후 sleep → 주기 밀림
    }
            }).start();
```

### ScheduledExecutorService
```java
scheduler.scheduleAtFixedRate(() -> {
        timestamps.add(System.currentTimeMillis());
doWork();  // 작업 시간 발생해도 다음 실행 시간은 고정
}, 0, intervalMs, TimeUnit.MILLISECONDS);
```

## 실제 테스트 결과

**테스트 환경**: 3초 동안 50ms 간격 샘플링

| 항목 | Manual Thread | ScheduledExecutorService |
|------|---------------|--------------------------|
| 총 샘플 수 | 60 | 60 |
| 목표 대비 정확도 | 101.7% | 101.7% |
| 평균 간격 | 50.8ms | 50.0ms |
| 간격 드리프트 | +0.8ms | +0.0ms |
| 표준편차 | 0.56ms | **0.00ms** ✅ |

**결과: ScheduledExecutorService 승리! 🏆**

### 해석

- **ScheduledExecutorService**는 표준편차 0.00ms로 완벽하게 일정한 간격 유지
- **Manual Thread**는 작업 시간(약 0.8ms)이 누적되어 드리프트 발생
- 간격이 정확해야 하는 모니터링, 벤치마크 등에서는 `ScheduledExecutorService` 권장

## 기술 스택

- Java 21
- Spring Boot 3.5.8
- Gradle