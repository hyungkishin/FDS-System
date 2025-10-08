# Kafka Streams

## KStream

### 개념
- **"무엇이 일어났는가"** 에 집중하는 이벤트 스트림이다.
- 하나의 사건(Event)처럼 취급 된다.
- 이벤트의 삭제 및 변경이 불가능 (Immutable) 하다.
- Append-only 방식으로 동작한다.

### 특징
변경 불가능한 이벤트의 연속으로, 과거와 현재의 모든 이벤트를 다룬다.

```text
{"userA", "상품-123-클릭"}
{"userB", "상품-234-클릭"}
{"userA", "상품-456-클릭"}
```

> 로그성 데이터를 처리하는데 적합하며, 모든 이벤트가 개별적인 의미를 가진다.

### 사용 사례

#### 1. 사기 탐지 (Fraud Detection)
지난 1분 동안 동일한 신용카드로 5번 이상 결제 시도가 발생한 패턴 감지

```kotlin
val paymentStream: KStream<String, PaymentEvent> = builder.stream("payments")

val fraudDetection = paymentStream
    .groupByKey()
    .windowedBy(TimeWindows.of(Duration.ofMinutes(1)))
    .count()
    .filter { _, count -> count >= 5 }
```

#### 2. 실시간 사용자 행동 분석
모든 클릭 이벤트를 실시간으로 분석하여 사용자 행동 패턴 추적

```kotlin
val clickStream: KStream<String, ClickEvent> = builder.stream("user-clicks")

clickStream
    .groupByKey()
    .windowedBy(TimeWindows.of(Duration.ofMinutes(5)))
    .aggregate(
        { ClickAggregate() },
        { key, value, aggregate -> aggregate.add(value) }
    )
```

---

## KTable

### 개념
- **"현재 상태가 무엇인가"** 에 집중하는 상태 테이블
- 특정 Key에 대한 최신 스냅샷을 유지
- Upsert 방식으로 동작 (Insert + Update)

### 특징
- 키에 해당하는 값이 없다면 Insert
- 키에 해당하는 값이 있다면 Update
- 관계형 데이터베이스의 테이블과 유사한 형태

### 동작 방식

```text
이벤트 순서:
1. { "user-A", "서울" }
2. { "user-A", "부산" }

KTable 결과:
{ "user-A", "부산" }  <- 최신 값만 유지
```

동일한 키에 대한 업데이트가 발생하면 이전 값은 덮어씌워진다.

### 사용 예

#### 1. 사용자 프로필 관리
```kotlin
val userProfiles: KTable<String, UserProfile> = builder.table("user-profiles")

// user-A의 위치가 여러 번 업데이트되어도 최신 값만 유지
```

#### 2. 재고 현황 추적
```kotlin
val inventoryTable: KTable<String, Int> = builder.table("inventory")

// 상품별 현재 재고 수량만 관리
```

---

## KStream과 KTable의 쌍대성 (Duality)

KStream과 KTable은 상호 변환이 가능한 쌍대성을 가진다.

### KStream → KTable 변환

스트림에 **집계 연산**을 적용하면 KTable로 변환된다.

```kotlin
// 주문 이벤트 스트림
val orderStream: KStream<String, Order> = builder.stream("orders")

// 고객별 총 주문 건수 (KTable)
val orderCountTable: KTable<String, Long> = orderStream
    .groupBy { key, order -> order.customerId }
    .count()
```

**설명**: 고객 ID를 기준으로 스트림을 그룹화하고 횟수를 세면, 각 고객의 현재까지 총 주문 건수를 저장하는 KTable이 생성된다.

### KTable → KStream 변환

KTable의 변경 사항을 이벤트 스트림으로 변환할 수 있다.

```kotlin
val userLocationTable: KTable<String, String> = builder.table("user-locations")

// 위치 변경 이벤트 스트림
val locationChangeStream: KStream<String, String> = userLocationTable.toStream()
```

---

## 정리

### KStream 사용 시기
- 모든 이벤트가 중요한 경우
- 로그 분석, 감사 추적
- 실시간 이벤트 처리
- 시계열 데이터 분석

### KTable 사용 시기
- 최신 상태만 중요한 경우
- 사용자 프로필, 설정 정보
- 재고, 잔고 등 현재 값 추적
- 참조 데이터 (Reference Data)

### 변환 기준
- **집계가 필요한가?** → KStream에서 KTable로
- **상태 변경을 추적하고 싶은가?** → KTable에서 KStream으로

---

## Kafka Streams API - 이상탐지(FDS) 예시

실시간 이상 거래 탐지 시스템을 구축할 때 사용하는 주요 Kafka Streams API들을 정리했습니다.

### 1. 기본 변환 API

#### filter() - 의심 거래 필터링
특정 조건에 맞는 이벤트만 선택

```kotlin
val paymentStream: KStream<String, Payment> = builder.stream("payments")

// 고액 거래만 필터링
val highValuePayments = paymentStream
    .filter { _, payment -> payment.amount > 1_000_000 }

// 해외 결제만 필터링
val foreignPayments = paymentStream
    .filter { _, payment -> payment.country != "KR" }
```

#### map() - 데이터 변환
이벤트를 다른 형태로 변환

```kotlin
// 결제 이벤트를 분석용 데이터로 변환
val analysisStream = paymentStream
    .map { key, payment ->
        KeyValue(
            payment.cardNumber,
            PaymentAnalysis(
                cardNumber = payment.cardNumber,
                amount = payment.amount,
                location = payment.location,
                timestamp = payment.timestamp,
                riskScore = calculateRiskScore(payment)
            )
        )
    }
```

#### flatMap() - 하나의 이벤트를 여러 이벤트로 분할
하나의 결제에서 여러 검증 포인트 생성

```kotlin
val verificationPoints = paymentStream
    .flatMap { key, payment ->
        listOf(
            KeyValue("amount-check-${payment.id}", AmountVerification(payment)),
            KeyValue("location-check-${payment.id}", LocationVerification(payment)),
            KeyValue("velocity-check-${payment.id}", VelocityVerification(payment))
        )
    }
```

---

### 2. 그룹화 및 집계 API

#### groupBy() / groupByKey() - 데이터 그룹화
카드번호 또는 사용자 ID 기준으로 그룹화

```kotlin
// 카드번호로 그룹화
val groupedByCard = paymentStream
    .groupBy { _, payment -> payment.cardNumber }

// 이미 키가 카드번호인 경우
val groupedByKey = paymentStream
    .selectKey { _, payment -> payment.cardNumber }
    .groupByKey()
```

#### count() - 건수 집계
특정 시간 내 거래 건수 계산

```kotlin
// 카드별 1분간 결제 시도 횟수
val paymentCountPerCard: KTable<Windowed<String>, Long> = paymentStream
    .selectKey { _, payment -> payment.cardNumber }
    .groupByKey()
    .windowedBy(TimeWindows.of(Duration.ofMinutes(1)))
    .count()

// 의심 거래 탐지: 1분에 5회 이상
val suspiciousCards = paymentCountPerCard
    .filter { _, count -> count >= 5 }
    .toStream()
```

#### aggregate() - 커스텀 집계
복잡한 통계 계산

```kotlin
data class CardStats(
    var totalAmount: Long = 0L,
    var count: Int = 0,
    var locations: MutableSet<String> = mutableSetOf(),
    var avgAmount: Double = 0.0
)

val cardStatistics = paymentStream
    .groupBy { _, payment -> payment.cardNumber }
    .windowedBy(TimeWindows.of(Duration.ofMinutes(5)))
    .aggregate(
        { CardStats() },  // 초기값
        { key, payment, stats ->  // 집계 로직
            stats.totalAmount += payment.amount
            stats.count += 1
            stats.locations.add(payment.location)
            stats.avgAmount = stats.totalAmount.toDouble() / stats.count
            stats
        }
    )

// 비정상 패턴 탐지: 5분간 3개 이상 다른 지역에서 결제
val locationAnomalies = cardStatistics
    .filter { _, stats -> stats.locations.size >= 3 }
    .toStream()
```

#### reduce() - 값 축소
두 값을 하나로 합치는 연산

```kotlin
data class PaymentSummary(
    val cardNumber: String,
    val totalAmount: Long,
    val lastPaymentTime: Long
)

val paymentSummaries = paymentStream
    .map { _, payment -> 
        KeyValue(payment.cardNumber, PaymentSummary(
            cardNumber = payment.cardNumber,
            totalAmount = payment.amount,
            lastPaymentTime = payment.timestamp
        ))
    }
    .groupByKey()
    .reduce { current, new ->
        PaymentSummary(
            cardNumber = current.cardNumber,
            totalAmount = current.totalAmount + new.totalAmount,
            lastPaymentTime = maxOf(current.lastPaymentTime, new.lastPaymentTime)
        )
    }
```

---

### 3. 윈도우 연산 API

시간 기반 이상탐지에 핵심적인 기능

#### Tumbling Window - 고정 크기 윈도우
겹치지 않는 고정 크기 시간 창

```kotlin
// 1분 단위로 카드별 결제 건수 집계
val tumblingWindow = paymentStream
    .groupBy { _, payment -> payment.cardNumber }
    .windowedBy(TimeWindows.of(Duration.ofMinutes(1)))
    .count()

// 시각화:
// [0-1분] [1-2분] [2-3분]
// 각 윈도우는 독립적
```

**사용 사례**: 매 분마다 독립적인 통계 생성, 정시 리포트

#### Hopping Window - 슬라이딩 윈도우
일정 간격으로 이동하는 겹치는 윈도우

```kotlin
// 5분 윈도우, 1분마다 이동
val hoppingWindow = paymentStream
    .groupBy { _, payment -> payment.cardNumber }
    .windowedBy(
        TimeWindows
            .of(Duration.ofMinutes(5))
            .advanceBy(Duration.ofMinutes(1))
    )
    .count()

// 시각화:
// [0-5분]
//   [1-6분]
//     [2-7분]
// 윈도우가 겹침
```

**사용 사례**: 이동 평균, 추세 분석

#### Sliding Window - 이벤트 기반 윈도우
이벤트 발생 시점 기준으로 윈도우 생성

```kotlin
// 각 결제 이벤트마다 이전 5분간의 데이터 집계
val slidingWindow = paymentStream
    .groupBy { _, payment -> payment.cardNumber }
    .windowedBy(SlidingWindows.withTimeDifferenceAndGrace(
        Duration.ofMinutes(5),
        Duration.ofSeconds(10)  // 지연 허용 시간
    ))
    .count()
```

**사용 사례**: 실시간 이상 탐지, 즉각적인 패턴 감지

#### Session Window - 활동 세션 기반 윈도우
비활성 기간으로 세션 구분

```kotlin
// 10분간 활동이 없으면 세션 종료
val sessionWindow = paymentStream
    .groupBy { _, payment -> payment.userId }
    .windowedBy(SessionWindows.with(Duration.ofMinutes(10)))
    .count()

// 한 세션 내 과도한 거래 탐지
val suspiciousSessions = sessionWindow
    .filter { _, count -> count > 20 }
    .toStream()
```

**사용 사례**: 사용자 활동 세션 분석, 비정상 활동 패턴 탐지

---

### 4. 조인 API

여러 데이터 소스를 결합하여 종합적인 분석

#### Stream-Stream Join
두 스트림의 이벤트를 시간 기준으로 조인

```kotlin
val paymentStream: KStream<String, Payment> = builder.stream("payments")
val locationStream: KStream<String, Location> = builder.stream("locations")

// 결제 시점의 위치 정보와 조인 (5분 윈도우)
val enrichedPayments = paymentStream
    .join(
        locationStream,
        { payment, location ->
            EnrichedPayment(
                payment = payment,
                actualLocation = location,
                isLocationMatch = payment.declaredLocation == location.actualLocation
            )
        },
        JoinWindows.of(Duration.ofMinutes(5))
    )

// 위치 불일치 탐지
val locationMismatch = enrichedPayments
    .filter { _, enriched -> !enriched.isLocationMatch }
```

#### Stream-Table Join
스트림 이벤트에 테이블 정보 보강

```kotlin
val paymentStream: KStream<String, Payment> = builder.stream("payments")
val userProfileTable: KTable<String, UserProfile> = builder.table("user-profiles")

// 결제 이벤트에 사용자 프로필 정보 추가
val paymentsWithProfile = paymentStream
    .selectKey { _, payment -> payment.userId }
    .join(
        userProfileTable,
        { payment, profile ->
            PaymentWithProfile(
                payment = payment,
                userTier = profile.tier,
                averageSpending = profile.averageMonthlySpending,
                registeredCards = profile.registeredCards
            )
        }
    )

// 평소 소비 패턴과 다른 거래 탐지
val unusualSpending = paymentsWithProfile
    .filter { _, data ->
        data.payment.amount > data.averageSpending * 3
    }
```

#### Left Join - 외부 조인
왼쪽 스트림의 모든 이벤트 유지

```kotlin
// 블랙리스트와 조인 (블랙리스트에 없어도 결제 이벤트는 유지)
val blacklistTable: KTable<String, BlacklistInfo> = builder.table("blacklist")

val checkedPayments = paymentStream
    .selectKey { _, payment -> payment.cardNumber }
    .leftJoin(
        blacklistTable,
        { payment, blacklistInfo ->
            PaymentRiskCheck(
                payment = payment,
                isBlacklisted = blacklistInfo != null,
                blacklistReason = blacklistInfo?.reason
            )
        }
    )

// 블랙리스트 카드 거래 차단
val blockedPayments = checkedPayments
    .filter { _, check -> check.isBlacklisted }
```

---

### 5. 분기 및 병합 API

#### branch() - 조건별 스트림 분기
하나의 스트림을 여러 조건으로 분리

```kotlin
val branches: Array<KStream<String, Payment>> = paymentStream
    .branch(
        { _, payment -> payment.amount > 5_000_000 },      // 0: 초고액 거래
        { _, payment -> payment.amount > 1_000_000 },      // 1: 고액 거래
        { _, payment -> payment.country != "KR" },         // 2: 해외 거래
        { _, _ -> true }                                    // 3: 일반 거래
    )

val veryHighValuePayments = branches[0]  // 추가 검증 필요
val highValuePayments = branches[1]       // 간단한 검증
val foreignPayments = branches[2]         // 위치 검증
val normalPayments = branches[3]          // 기본 처리

// 각 분기마다 다른 처리
veryHighValuePayments
    .filter { _, payment -> !isVerified(payment) }
    .to("fraud-alerts")
```

#### merge() - 여러 스트림 병합
여러 스트림을 하나로 합침

```kotlin
val cardPayments: KStream<String, Payment> = builder.stream("card-payments")
val mobilePayments: KStream<String, Payment> = builder.stream("mobile-payments")
val bankTransfers: KStream<String, Payment> = builder.stream("bank-transfers")

// 모든 결제 수단을 하나의 스트림으로 통합
val allPayments = cardPayments
    .merge(mobilePayments)
    .merge(bankTransfers)

// 통합된 스트림에서 이상 패턴 탐지
val anomalies = allPayments
    .groupBy { _, payment -> payment.userId }
    .windowedBy(TimeWindows.of(Duration.ofMinutes(5)))
    .aggregate(
        { TransactionPattern() },
        { _, payment, pattern -> pattern.add(payment) }
    )
    .filter { _, pattern -> pattern.isAnomalous() }
```

---

### 6. 출력 API

#### to() - 토픽에 결과 전송
처리 결과를 다른 Kafka 토픽으로 전송

```kotlin
// 의심 거래를 별도 토픽으로 전송
suspiciousPayments.to("fraud-alerts")

// 처리 결과를 Produced 옵션과 함께 전송
normalPayments.to(
    "processed-payments",
    Produced.with(Serdes.String(), paymentSerde)
)
```

#### foreach() - 사이드 이펙트 처리
각 레코드마다 외부 시스템 호출 (DB 저장, 알림 등)

```kotlin
// 고위험 거래 발견 시 실시간 알림
highRiskPayments.foreach { key, payment ->
    // 외부 시스템 호출
    notificationService.sendAlert(payment)
    auditLogger.log(payment)
}
```

#### peek() - 디버깅 및 로깅
스트림을 변경하지 않고 중간 상태 확인

```kotlin
val processed = paymentStream
    .filter { _, payment -> payment.amount > 100_000 }
    .peek { key, payment -> 
        logger.info("High value payment detected: $key, ${payment.amount}")
    }
    .map { key, payment -> enrichPayment(payment) }
    .peek { key, enriched ->
        logger.info("Enriched payment: $key")
    }
```

---

### 실전 예시: 종합 이상탐지 시스템

위의 API들을 조합한 실전 이상탐지 시스템 예시

```kotlin
class FraudDetectionTopology {
    
    fun buildTopology(builder: StreamsBuilder): Topology {
        
        // 1. 입력 스트림
        val payments: KStream<String, Payment> = builder.stream("payments")
        val userProfiles: KTable<String, UserProfile> = builder.table("user-profiles")
        val blacklist: KTable<String, BlacklistInfo> = builder.table("blacklist")
        
        // 2. 블랙리스트 체크
        val blacklistChecked = payments
            .selectKey { _, payment -> payment.cardNumber }
            .leftJoin(blacklist) { payment, blacklistInfo ->
                PaymentWithBlacklist(payment, blacklistInfo)
            }
        
        // 3. 스트림 분기
        val branches = blacklistChecked.branch(
            { _, data -> data.blacklistInfo != null },     // 블랙리스트
            { _, data -> data.payment.amount > 5_000_000 }, // 초고액
            { _, data -> data.payment.amount > 1_000_000 }, // 고액
            { _, _ -> true }                                 // 일반
        )
        
        val blacklistedPayments = branches[0]
        val veryHighValue = branches[1]
        val highValue = branches[2]
        val normal = branches[3]
        
        // 4. 블랙리스트 거래 즉시 차단
        blacklistedPayments
            .mapValues { data -> 
                FraudAlert(
                    payment = data.payment,
                    reason = "BLACKLISTED_CARD",
                    riskLevel = RiskLevel.CRITICAL
                )
            }
            .to("fraud-blocked")
        
        // 5. 속도 기반 탐지 (Velocity Check)
        val velocityCheck = normal
            .selectKey { _, data -> data.payment.cardNumber }
            .groupByKey()
            .windowedBy(TimeWindows.of(Duration.ofMinutes(1)))
            .count()
            .filter { _, count -> count >= 5 }
            .toStream()
            .map { windowed, count ->
                KeyValue(
                    windowed.key(),
                    FraudAlert(
                        cardNumber = windowed.key(),
                        reason = "HIGH_VELOCITY",
                        count = count,
                        riskLevel = RiskLevel.HIGH
                    )
                )
            }
        
        // 6. 위치 기반 탐지
        val locationCheck = normal
            .selectKey { _, data -> data.payment.cardNumber }
            .groupByKey()
            .windowedBy(TimeWindows.of(Duration.ofMinutes(5)))
            .aggregate(
                { LocationTracker() },
                { _, data, tracker ->
                    tracker.addLocation(data.payment.location)
                    tracker
                }
            )
            .filter { _, tracker -> tracker.suspiciousLocationPattern() }
            .toStream()
            .mapValues { tracker ->
                FraudAlert(
                    reason = "SUSPICIOUS_LOCATION_PATTERN",
                    locations = tracker.locations,
                    riskLevel = RiskLevel.MEDIUM
                )
            }
        
        // 7. 금액 패턴 분석
        val amountCheck = highValue
            .selectKey { _, data -> data.payment.userId }
            .join(
                userProfiles,
                { data, profile ->
                    PaymentWithProfile(data.payment, profile)
                }
            )
            .filter { _, data ->
                // 평소 소비의 5배 이상
                data.payment.amount > data.profile.averageSpending * 5
            }
            .mapValues { data ->
                FraudAlert(
                    payment = data.payment,
                    reason = "UNUSUAL_AMOUNT",
                    riskLevel = RiskLevel.MEDIUM
                )
            }
        
        // 8. 모든 의심 거래 통합
        val allFraudAlerts = velocityCheck
            .merge(locationCheck)
            .merge(amountCheck)
        
        // 9. 최종 출력
        allFraudAlerts.to("fraud-alerts")
        
        // 10. 정상 거래 처리
        normal
            .mapValues { data -> data.payment }
            .to("processed-payments")
        
        return builder.build()
    }
}

// 보조 클래스
data class LocationTracker(
    val locations: MutableSet<String> = mutableSetOf()
) {
    fun addLocation(location: String): LocationTracker {
        locations.add(location)
        return this
    }
    
    fun suspiciousLocationPattern(): Boolean {
        // 5분 안에 3개 이상의 다른 도시
        return locations.size >= 3
    }
}

data class PaymentWithBlacklist(
    val payment: Payment,
    val blacklistInfo: BlacklistInfo?
)

data class PaymentWithProfile(
    val payment: Payment,
    val profile: UserProfile
)

data class FraudAlert(
    val payment: Payment? = null,
    val cardNumber: String? = null,
    val reason: String,
    val riskLevel: RiskLevel,
    val count: Long? = null,
    val locations: Set<String>? = null
)

enum class RiskLevel {
    LOW, MEDIUM, HIGH, CRITICAL
}
```

---

### API 선택 가이드

#### 언제 어떤 API를 사용할까?

**필터링이 필요할 때**
- `filter()`: 단순 조건 필터링
- `branch()`: 여러 조건으로 분기

**데이터 변환이 필요할 때**
- `map()`: 1:1 변환
- `flatMap()`: 1:N 변환

**집계가 필요할 때**
- `count()`: 단순 건수
- `reduce()`: 두 값을 하나로
- `aggregate()`: 복잡한 통계

**시간 기반 분석이 필요할 때**
- `Tumbling Window`: 독립적인 시간 단위
- `Hopping Window`: 겹치는 시간 단위
- `Sliding Window`: 연속적인 실시간 분석
- `Session Window`: 활동 세션 기반

**여러 데이터를 결합할 때**
- `Stream-Stream Join`: 두 이벤트 스트림
- `Stream-Table Join`: 이벤트 + 참조 데이터
- `merge()`: 단순 병합

**결과 출력이 필요할 때**
- `to()`: Kafka 토픽으로
- `foreach()`: 외부 시스템으로
- `peek()`: 로깅/디버깅
