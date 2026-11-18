# ADR-000: 주기 작업의 실행 간격 — `Thread.sleep` 루프 vs `ScheduledExecutorService`

- 상태: 끝남 — 결과 커밋 8cfd01d(커밋일 2026-01-17). 측정일은 기록 없음
- 정리: 2026-11-18. 이 저장소에는 따로 쓴 설계 · 실험 기록 문서가 없어서, README의 "비교 항목" · "실제 테스트 결과" · "해석" 절과 코드를 이 형식으로 모았다. README 원문은 그대로 남아 있다.

## 질문

같은 주기 작업을 직접 만든 스레드의 `while` + `Thread.sleep(interval)` 루프로 돌릴 때와 `ScheduledExecutorService.scheduleAtFixedRate`로 돌릴 때, 실제 실행 간격이 목표 간격에서 얼마나 벗어나고 얼마나 일정한가.

## 선택지

| 선택지 | 코드 | 간격을 정하는 방식 |
|---|---|---|
| `Thread` + `sleep` | `sampler/ManualThreadSampler.java` | 작업을 마친 뒤 `Thread.sleep(intervalMs)`. 다음 실행은 "작업 시간 + interval" 뒤 |
| `ScheduledExecutorService` | `sampler/ScheduledThreadSampler.java` | `scheduleAtFixedRate(task, 0, intervalMs, MILLISECONDS)`. 다음 실행 시각은 시작 시각 기준으로 고정 |

README의 비교 항목 표(정리 전 원문. 2026-11-18에 README의 "예외 처리" 행은 JDK Javadoc에 맞춰 고쳤다 — 아래 한계):

| 항목 | Manual Thread + Sleep | ScheduledExecutorService |
|------|----------------------|--------------------------|
| 주기 정확도 | 작업 시간만큼 밀림 | 정확한 주기 유지 시도 |
| 예외 처리 | 스레드가 죽을 수 있음 | 예외 발생해도 계속 실행 |
| 종료 처리 | interrupt + join 필요 | cancel() 한 줄 |
| 코드 복잡도 | while 루프 직접 관리 | 프레임워크가 관리 |
| 리소스 관리 | 매번 새 스레드 생성 | 스레드 풀 재사용 |

이 표에서 측정한 것은 "주기 정확도" 행뿐이다. 나머지 행은 측정하지 않은 설명이다(아래 한계).

## 실험 설계

- 실행 경로: `GET /api/compare?interval=&duration=`(interval 10~1000ms, duration 1~30초로 제한), `GET /api/quick-test`(50ms · 3초), `GET /api/detailed-test`(100ms · 10초) — `controller/ComparisonController.java`.
- 순서: `sleep` 방식을 먼저 끝까지 돌리고, 500ms 쉰 뒤 `ScheduledExecutorService` 방식을 돌린다. 두 방식이 동시에 돌지 않는다 — `service/ComparisonService.java`.
- 매 실행에서 하는 일: 실행 시각을 `System.currentTimeMillis()`로 기록, `OperatingSystemMXBean.getCpuLoad()` 1회, 난수 1,000~4,999개를 더하는 작업 1회(`simulateWork`).
- 지표(두 sampler의 `buildResult`가 같은 식으로 계산):
  - 간격 = 연속한 두 실행 시각의 차
  - 평균 간격 · 최소 · 최대 · 표준편차(모표준편차)
  - 간격 드리프트 = 평균 간격 − 목표 간격(`ComparisonService.buildSummary`)
  - 정확도: 코드는 `expectedSamples = (int) (actualDuration / intervalMs)`, `accuracy = timestamps.size() / expectedSamples × 100`이다. `actualDuration` = 마지막 실행 시각 − 시작 시각(설정 시간 3,000ms가 아니라 실제 걸린 시간). 이 측정에서는 `sleep` 방식이 2,997ms ÷ 50 → 59(정수 나눗셈), `ScheduledExecutorService`가 2,950ms ÷ 50 = 59라 둘 다 60 ÷ 59 = 101.7%가 된다(실제 길이는 평균 간격 × 59로 계산한 값)
- API 응답의 `winner`는 "표준편차 + max(0, 100 − 정확도)"가 작은 쪽이다(`ComparisonService.determineWinner`).
- 측정 조건: Java 21 · Spring Boot 3.5.8(`build.gradle`). 하드웨어 · OS · JDK 빌드는 기록 없음.

## 결과

README "실제 테스트 결과" 절(원문 그대로, 커밋일 2026-01-17):

**테스트 환경**: 3초 동안 50ms 간격 샘플링

| 항목 | Manual Thread | ScheduledExecutorService |
|------|---------------|--------------------------|
| 총 샘플 수 | 60 | 60 |
| 목표 대비 정확도 | 101.7% | 101.7% |
| 평균 간격 | 50.8ms | 50.0ms |
| 간격 드리프트 | +0.8ms | +0.0ms |
| 표준편차 | 0.56ms | **0.00ms** ✅ |

앱이 고른 쪽(API 응답의 `winner`)은 `ScheduledExecutorService`였다. 앱이 내보내는 문자열은 "ScheduledExecutorService 승리! 🏆"이다.

출처: `README.md` "실제 테스트 결과" 절. 원본 응답(JSON)은 저장소에 없다.

### 해석 (README 원문)

- **ScheduledExecutorService**는 표준편차 0.00ms로 완벽하게 일정한 간격 유지
- **Manual Thread**는 작업 시간(약 0.8ms)이 누적되어 드리프트 발생
- 간격이 정확해야 하는 모니터링, 벤치마크 등에서는 `ScheduledExecutorService` 권장

## 결정

실행 간격이 일정해야 하는 주기 작업에는 `ScheduledExecutorService.scheduleAtFixedRate`를 쓴다. `sleep` 루프는 작업 · 측정 호출 · sleep 지연이 매 주기에 더해져 평균 간격이 목표보다 길어진다(이 측정에서 +0.8ms. 각각은 따로 재지 않았다). 커밋 8cfd01d의 메시지는 이 결론과 함께 `ScheduledExecutorService`를 더 공부할 필요가 있다고 남겼다.

## 한계

- 측정은 README에 남은 1회분뿐이다. 반복했는지, 하드웨어 · OS가 무엇이었는지는 기록 없음.
- 실행 시각을 `System.currentTimeMillis()`(1ms 단위)로 기록했다. 표준편차 0.00ms는 "모든 간격이 정수 50ms로 기록됐다"는 뜻이고, 1ms보다 작은 변동은 이 측정으로 보이지 않는다. `System.nanoTime()`으로 다시 재면 달라질 수 있다.
- 작업 시간은 코드 주석에 "1~5ms"로 적혀 있지만, 측정된 드리프트는 0.8ms였다. 작업 시간을 따로 재지 않았다.
- 정확도 101.7%는 두 방식이 같다. 분모를 설정 시간이 아니라 실제 걸린 시간(마지막 실행 시각 − 시작 시각)으로 잡아, 이 값은 "목표 시간 안에 몇 번 실행했나"를 비교하지 못한다.
- 비교 항목 표의 예외 처리 · 종료 처리 · 코드 복잡도 · 리소스 관리 행은 이 실험에서 재지 않았다.
- 원문 표의 "ScheduledExecutorService — 예외 발생해도 계속 실행"은 JDK Javadoc과 반대다. `scheduleAtFixedRate`의 Javadoc은 반복 작업에서 예외가 밖으로 나오면 이후 실행을 멈춘다고 적는다. README 표는 2026-11-18에 Javadoc대로 고쳤다. 같은 설명이 `sampler/ScheduledThreadSampler.java`의 클래스 주석("예외 발생해도 스케줄링 계속")에도 있지만, 코드는 고치지 않았다.
- 작업 시간이 interval보다 긴 경우, 여러 주기 작업을 한 스케줄러에 올리는 경우, `scheduleWithFixedDelay`와의 비교는 다루지 않았다.
