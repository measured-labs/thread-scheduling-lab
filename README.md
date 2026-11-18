# Thread Sampling 방식 비교 프로젝트

## 결과와 결론

이 실험은 같은 주기 작업을 `new Thread` + `Thread.sleep` 루프로 돌릴 때와 `ScheduledExecutorService.scheduleAtFixedRate`로 돌릴 때 실제 실행 간격이 얼마나 다른지 물었다. 두 방식에 같은 가벼운 작업(난수 1,000~4,999개 합산)을 넣고, 3초 동안 50ms 간격으로 실행 시각을 기록해 평균 간격과 표준편차를 쟀다. `sleep` 루프는 평균 간격이 50.8ms로 목표보다 0.8ms 길었고(작업 · 측정 호출 · sleep 지연이 매 주기에 더해짐 — 각각 따로 재지 않음), `scheduleAtFixedRate`는 평균 50.0ms에 표준편차 0.00ms였다. 결론: 실행 간격이 일정해야 하는 주기 작업(모니터링 샘플링 등)에는 `ScheduledExecutorService`를 쓴다. 단, 기록된 측정은 1회뿐이고, 실행 시각을 `System.currentTimeMillis()`(1ms 단위)로 남겨 1ms보다 작은 차이는 보이지 않는다.

| 항목 (3초 · 50ms 간격) | `Thread` + `sleep` | `ScheduledExecutorService` | 출처 |
|---|---:|---:|---|
| 샘플 수 | 60 | 60 | 아래 "실제 테스트 결과" 표 (README.md:79) |
| 평균 간격 | 50.8ms | 50.0ms | README.md:81 |
| 목표 대비 간격 차이 | +0.8ms | +0.0ms | README.md:82 |
| 간격 표준편차 | 0.56ms | 0.00ms | README.md:83 |

- 측정 조건: Java 21 · Spring Boot 3.5.8(`build.gradle`). 3초 · 50ms는 `GET /api/quick-test`의 설정과 같다(`ComparisonController.java`). 하드웨어 · OS · JDK 빌드는 기록 없음. README에 남은 결과는 1회분이고, 반복했는지는 기록 없음. 측정일은 기록 없음 — 결과를 커밋한 날은 2026-01-17(커밋 8cfd01d)이다.
- 질문 · 설계 · 결과 · 한계는 [ADR-000](docs/adr/ADR-000-scheduling-interval-accuracy.md)에 정리했다.

Java 21 + Spring Boot 3.5.8 기반

## 개요

**Manual Thread + Sleep** vs **ScheduledExecutorService** 두 방식의 주기적 작업 실행을 비교합니다.

## 비교 항목

| 항목 | Manual Thread + Sleep | ScheduledExecutorService |
|------|----------------------|--------------------------|
| 주기 정확도 | 작업 시간만큼 밀림 | 정확한 주기 유지 시도 |
| 예외 처리 | 스레드가 죽을 수 있음 | 반복 작업에서 예외가 밖으로 나오면 이후 실행이 멈춘다(JDK `scheduleAtFixedRate` Javadoc. 이 저장소에서는 측정하지 않음) |
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

**결과**: 앱이 고른 쪽(API 응답의 `winner` — 표준편차와 정확도로 계산)은 `ScheduledExecutorService`였다. 앱이 내보내는 문자열은 "ScheduledExecutorService 승리! 🏆"이다.

### 해석

- **ScheduledExecutorService**는 표준편차 0.00ms로 완벽하게 일정한 간격 유지
- **Manual Thread**는 작업 시간(약 0.8ms)이 누적되어 드리프트 발생
- 간격이 정확해야 하는 모니터링, 벤치마크 등에서는 `ScheduledExecutorService` 권장

## 기술 스택

- Java 21
- Spring Boot 3.5.8
- Gradle