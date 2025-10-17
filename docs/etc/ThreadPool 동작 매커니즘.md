# Thread Pool 동작 매커니즘

## ThreadPool 개념은 다음과 같다.
1. Core Pool Size (코어 스레드 수)
- 항상 살아있는 기본 스레드 개수 이다.
- 요청이 없어도 유지되는 스레드 이다.

2. Max Pool Size (최대 스레드 수)
- 동시에 생성할 수 있는 최대 스레드 개수이다.
- Core를 초과하면 Queue에 쌓이고, Queue가 가득 차면 Max까지 늘어난다.

3. Queue Capacity (큐 용량)
- Core Pool이 모두 사용 중일 때 대기할 작업을 저장하는 공간이다.

4. Keep Alive Time
- Core 이상으로 생성된 유휴 스레드가 종료되기까지의 시간

## 송금 비즈니스에서 ThreadPool 흐름 정리
```text
[Main Thread] 송금 API 요청
     ↓
[Main Thread] TransactionService.createTransfer() 시작
     ↓
[Main Thread] DB 트랜잭션 처리 (입출금, 저장)
     ↓
[Main Thread] eventPublisher.publishEvent(completeEvent) ← 이벤트 발행
     ↓
     ├─→ [Main Thread] API 응답 즉시 반환
     │
     └─→ [Thread Pool] outboxEventExecutor에서 새 스레드 할당
              ↓
         [Worker Thread] TransferOutboxEventHandler.handle() 실행
              ↓
         [Worker Thread] Kafka 메시지 전송
```

## 코드레벨

```kotlin
@Configuration
@EnableAsync
class AsyncConfig {

    @Bean("outboxEventExecutor")
    fun outboxEventExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()

        // 200 TPS 처리를 위한 설정
        executor.corePoolSize = 10              // 평시 10개 스레드 유지
        executor.maxPoolSize = 30               // 피크시 30개까지 확장
        executor.queueCapacity = 200            // 200개까지 큐 대기
        executor.setThreadNamePrefix("outbox-event-")

        // CallerRunsPolicy 대신 AbortPolicy 사용
        // 포화 시 예외 발생시, Outbox 패턴으로 재처리 한다
        executor.setRejectedExecutionHandler(ThreadPoolExecutor.AbortPolicy())

        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.setAwaitTerminationSeconds(30)
        executor.setTaskDecorator(mdcTaskDecorator())

        executor.initialize()
        return executor
    }
}
```

## ThreadPool 이 가득 찼을때 
```text
- Core 스레드: 10개 (모두 실행 중 가정)
- Max 스레드: 30개 (모두 실행 중 가정)
- Queue: 200개 (가득 찬다고 가정)
```

> 이때 231번째 요청이 들어오면, 기냥 포화상태가 된다.

## 개발자가 알아야 지식은 AbortPolicy 

