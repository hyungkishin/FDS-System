## W3C Trace Context 란 ?
W3C Trace Context는 **분산 추적(distributed tracing)** 을 위한 국제 표준이다.  

마이크로서비스, 메시지 브로커, 외부 API 호출 등 여러 시스템을 거치는 하나의 요청을 동일한 TraceId로 추적할 수 있게 해주는 규격을 말한다.

## 개념
traceparent 헤더
```
traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
```

- `00`  = 버전
- `4bf9...4736` = traceId (16바이트 = 32자리 hex, 전부 0이면 invalid)
- `00f0...02b7` = spanId (8바이트 = 16자리 hex)
- `01` = traceFlags (샘플링 여부 등)

## 왜 필요하지?
여러 서비스가 얽힌 환경에서,   
**“이 요청이 어디서 왔고, 어떤 서비스들을 거쳤는지”** 를 추적해야 장애 원인을 알 수 있기 때문이다.  

> 예를 들면, user-service → order-service → payment-service  
> 모두 같은 traceId를 공유하게 될 시, 로그와 메트릭을 모으면 하나의 호출 체인으로 볼 수 있다.

## 스펙에서 중요한 제약
traceId는 반드시 16바이트 (32자리 hex) 일것.  

**전부 0(0000…0000)** 은 invalid 새로 생성해야 할 것.  
- 어떤 시스템이 traceId를 초기화만 해두고 값 안채울 경우 발생 할 수 있다.
- 이 값을 “trace 없음”(null처럼) 표현하는 예약값으로 쓰기 때문이기도 하다.
- 샘플링 거부 등으로 trace를 이어받지 못했을 때, 이런 케이스에서 000…0이 들어올 수 있다.

spanId도 반드시 8바이트 (16자리 hex) 일것.

## spanId란?
trace 안에서의 **작업 단위(Span)**를 식별하는 ID 이다.  

traceId가 “요청 전체 흐름”이라면, spanId는 “그 안의 개별 구간” 이라고 보면 된다.

e.g )  
사용자가 결제 API 호출 -> traceId = abc...123

API 내부에서 DB 조회를 하는 Span -> spanId = 1111...aaaa

외부 결제 PG사 호출하는 Span -> spanId = 2222...bbbb

응답 변환하는 Span -> spanId = 3333...cccc

### SpanId 가 도대체 왜 중요할까...?
로그/메트릭을 수집할 때 traceId만 있으면 “같은 요청”이라는 건 알 수 있지만,  
요청 안의 어떤 단계가 느렸는지는 알 수가 없기 때문이다.  

spanId가 있어야 “DB 조회에서 300ms, 외부 PG 호출에서 1200ms” 같은 세부 성능 추적이 가능해진다.  

그래서 APM(Zipkin, Jaeger, Tempo, Datadog 등)에서는 traceId + spanId를 항상 같이 쓴다고 한다.  