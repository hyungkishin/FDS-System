## 들어가며

헥사고날 아키텍처 기반으로 시작해 프로젝트 뼈대를 잡은지 어느덧 1달이 되어간다.  

그러다 헥사고날 아키텍처와 DDD 방법론을 적용해 멀티모듈 구조로 확장했고, 그 과정에서 공통 응답코드 설계를 하면서 많은 고민을 하게 되었다.

단순히 코드를 나누는 문제가 아니라, 모듈 간 의존성을 정리하게 되었고 도메인 순수성을 지키면서, 팀 전체가 따라야 할 규칙을 코드로 강제하는 과정을 경험하니

“유지보수를 최적화하는 전략”이라는 말이 추상적으로 들렸는데, 직접 구현하니 왜 그게 방파제 역할을 하는지 피부로 느껴졌다.

규칙과 제약은 많아졌지만, 그게 없으면 구조는 언젠가 무너진다.

특히 멀티모듈 환경에서 컨벤션을 지키고, 공통 코드를 일관되게 유지하는 일은 팀 생산성과 비용에 많은 영향을 준다.

속도만 보다가 구조를 세우기 위한 높은 운영비용을 경험하신 개발자들을 여러 번 봤기에, 이번엔 처음부터 ‘방파제’를 세우고 싶었다.

지금의 시도는 단순한 설계 연습이 아니라, 미래 유지보수 비용을 줄이기 위한 선불 비용이라 생각하고 정리해보자.

> 팀마다 / 운영 환경에 따라, instance 를 올리는 상황 이 발생하게 되면 운영 cost 가 올라가게 된다.   
> 
> 어느정도 중복을 허용하는것 + domain ( application 에 common 을 두고 작업 )  
> 사람에 따라서 (운용 능력) 이 갈리게 되니. 
> 
> 경험을 기반한 최선의 선택이 결국 남들이 보았을때도 납득이 가야된다.   
> 
> 나아가서 It shake it 좀 치는데... 라는 말을 들으면 더 좋고 ( 최고의 성취 도파민 )   

# 헥사고날 + DDD 운영 매뉴얼을 잡는다면.

## 철학과 방향성 측면

> **“헥사고날 아키텍처 + DDD = 변경 관리 전략이다”**  
> 설계의 목적은 구조를 이쁘게 하는 것이 아니라, **변경 비용과 리스크를 최소화**하는 것이다.

체크리스트 는 다음과 같다
- [ ] 유지보수 / 확장에 최적 ?
- [ ] 변경 영향 범위가 예측 가능 ? <strike>(이거 솔직히 신 아니면 아무도 모르지 않나 ..? )</strike>
- [ ] 도메인 모델의 순수성이 보존 되는가 ?
- [ ] 인프라 / 전달 레이어 기술 교체에 유연 하는가 ?
- [ ] 팀 간 의사소통 비용 최소화 <strike>(이것도 솔직히 사람 잘 못 만나면 지옥 아닌가 ? )</strike>

> 정정.. 변경 영향 범위가 예측 되려면 사실 아빠가 이재ㅁ.. 이여도 힘들 읍읍.. 
---

## 모듈 구조

```
:shared-common           # 공통 도메인 오류 모델 (Spring) [X]
:delivery-http-error           # 전역 예외 핸들러, ErrorResponse (Spring Web) [V]

:transfer-domain         # 도메인 엔티티/정책
:transfer-application    # 유스케이스/트랜잭션 (Web 모름)
:transfer-infra          # RDB/Kafka/외부 API 어댑터

:api                     # HTTP 엔드포인트 (Controller)
```

---

## 3. 의존성 규칙

| From \ To            | shared-common | delivery-http-error | transfer-domain | transfer-application | transfer-infra |     api |
|----------------------|--------------:|--------------------:|----------------:|---------------------:|---------------:|--------:|
| shared-common        |             — |                🙅🏻 |            🙅🏻 |                 🙅🏻 |           🙅🏻 |    🙅🏻 |
| delivery-http-error  |       🙆🏻‍♀️ |                   — |            🙅🏻 |                 🙅🏻 |           🙅🏻 | 🙆🏻‍♀️ |
| transfer-domain      |       🙆🏻‍♀️ |                🙅🏻 |               — |                 🙅🏻 |           🙅🏻 |    🙅🏻 |
| transfer-application |       🙆🏻‍♀️ |                🙅🏻 |         🙆🏻‍♀️ |                    — |        🙆🏻‍♀️ |    🙅🏻 |
| transfer-infra       |       🙆🏻‍♀️ |                🙅🏻 |         🙆🏻‍♀️ |              🙆🏻‍♀️ |              — |    🙅🏻 |
| api                  |       🙆🏻‍♀️ |             🙆🏻‍♀️ |         🙆🏻‍♀️ |              🙆🏻‍♀️ |        🙆🏻‍♀️ |       — |

- **application -> web 의존 불가능**
- **api 만** web 의존 가능
- **shared-* 는 Spring 의존 X*

---

## 레이어 책임

### Domain

- 도메인 엔티티, 값 객체, 애그리게잇, 정책
- 비즈니스 규칙 검증
- **외부 기술/전달수단 모름**

### Application

- 유스케이스(서비스)
- 트랜잭션 경계
- 포트 인터페이스 정의
- **HttpStatus/ResponseEntity 모름**

### Infrastructure

- 포트 구현체 (RDB, Kafka, 외부 API)
- 인프라 예외 → 도메인 오류 변환
- 기술 스택 의존 허용

### Delivery(API)

- 컨트롤러
- 전역 예외 핸들러
- 도메인 오류 → HTTP 응답 매핑

---

## 예외/오류 처리

### 공통 도메인 오류 (`shared-common`)

```kotlin
sealed interface DomainError {
    val code: String
    val message: String
    val meta: Map<String, Any?> get() = emptyMap()
}

class DomainException(val error: DomainError) : RuntimeException(error.message)
```

### 공통 오류 예시

```kotlin
sealed interface CommonError : DomainError {
    data class NotFound(val resource: String, val id: String) : CommonError { ... }
    data class InvalidArgument(val field: String, val reason: String?) : CommonError { ... }
    object Timeout : CommonError { ... }
    data class Conflict(val reason: String) : CommonError { ... }
    data class ExternalDependencyError(val service: String, val detail: String?) : CommonError { ... }
}
```

### 도메인 전용 오류 예시 (`transfer-domain`)

```kotlin
sealed interface TransferError : DomainError
data class InsufficientBalance(val current: Long, val request: Long) : TransferError { ... }
```

---

## 인프라 예외 변환 (`safeDbCall`)

```kotlin
inline fun <T> safeDbCall(block: () -> T): T =
    try {
        block()
    } catch (t: Throwable) {
        val err = InfraErrorTranslator.translate(t) ?: throw t
        throw DomainException(err, t)
    }
```

Spring/JPA 예외 매핑 등록:

```kotlin
@Configuration
class InfraErrorSpringRules {
    init {
        InfraErrorTranslator.register { t ->
            when (t) {
                is EmptyResultDataAccessException -> CommonError.NotFound("unknown", "unknown-id")
                is DataIntegrityViolationException -> CommonError.Conflict("duplicate-key")
                is QueryTimeoutException -> CommonError.Timeout
                is CannotCreateTransactionException -> CommonError.ExternalDependencyError("rdb", t.message)
                else -> null
            }
        }
    }
}
```

---

## 의존성 강제 도구

- **Gradle 스크립트**: 허용 매트릭스 위반 시 빌드 실패
- **ArchUnit**: 레이어 간 금지 의존성 테스트에서 차단
- **Version Catalog**: 버전 중앙화
- **Convention Plugin**: 공통 Kotlin/Spring 설정 적용

---

## 온보딩 가이드

1. 새 도메인 생성 시 `*-domain` / `*-application` / `*-infra` 모듈 생성
2. 포트 인터페이스(application) → 어댑터 구현(infra)
3. 오류는 DomainError 상속
4. API 계층에서 DomainException을 HTTP로 매핑
5. infra는 `safeDbCall` 사용하여 예외 변환

---

## 팁

- 규칙은 반드시 **코드로 강제** -> 말로만 하면 깨짐
- 새 모듈 추가 시 **의존 매트릭스 표**에 반영
- 전역 예외 변환 규칙은 한 곳에서만 수정
- 샘플/템플릿 모듈을 만들어 신규 개발자는 그대로 복붙 시작
- Event Driven 시나리오는 이벤트 네이밍/스키마 버전 관리 규칙 추가
