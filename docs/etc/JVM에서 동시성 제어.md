# JVM에서의 동시성 제어 메커니즘

자바(JVM)는 여러 작업(스레드)이 동시에 실행될 때 문제가 생기지 않도록 **'모니터(Monitor)'라는 잠금 장치**를 활용한다.  
쉽게 말해, **"하나씩 차례로 들어가게 하자"** 라는 개념이다.

---

## 뮤텍스 (Mutex)

- **Mutual Exclusion = 상호 배제**
- 동시에 **하나의 스레드만 자원을 사용할 수 있도록 막아주는 장치**

> 화장실은 한 명씩만 사용 가능. 문을 잠그면 다른 사람은 기다려야 한다.

자바의 `synchronized`는 사실 이 뮤텍스를 **자동으로** 사용하는 문법이다.

---

## 세마포어 (Semaphore)

- 뮤텍스를 **N개로 확장한 것**
- 예를 들어, 동시에 5명까지 사용 가능하도록 허용

> 주차장이 5칸이면, 차 5대까지만 들어올 수 있고, 나가야 다음 차가 들어올 수 있다.

```kotlin
val semaphore = Semaphore(5)

fun accessResource() {
    semaphore.acquire()   // 들어가기
    try {
        // 자원 사용
    } finally {
        semaphore.release()  // 나가기
    }
}
```

---

## 모니터 (Monitor)

- JVM의 모든 객체는 **모니터라는 기능**을 기본으로 갖고 있다
- 모니터는 아래 두 가지 기능을 한다:

```
모니터 = 뮤텍스(잠금장치) + 조건 기다림(wait/notify)
```

### 예를 들면, 화장실 + 대기줄
- 문이 잠기면 다른 사람은 기다림 (`lock`)
- 어떤 조건이 충족되면 다음 사람을 불러줌 (`notify`)

```java
synchronized(obj) {
    while (!조건) {
        obj.wait();     // 조건을 기다림
    }
    // 조건 충족됨 -> 작업 실행
    obj.notify();       // 다른 사람 불러줌
}
```

---

## JVM과 `synchronized`는 어떤 관계 일까

`synchronized(obj)`는  
-> **obj의 "모니터"를 얻어야만** 실행 가능하다.

```java
synchronized(obj) {
    // obj의 문을 잠궈야 들어갈 수 있다
}
```

JVM 내부에서는 다음처럼 처리된다:

| 바이트코드 명령어 | 의미                    |
|------------------|-------------------------|
| `monitorenter`   | 모니터(락) 획득         |
| `monitorexit`    | 모니터(락) 해제         |

---

## ☕ Kotlin의 @Synchronized

```kotlin
@Synchronized
fun criticalSection() {
    // 한 스레드만 접근 가능
}
```

-> Java 코드로 보면 아래와 같음:

```java
public synchronized void criticalSection() {
    // ...
}
```

🛑 단점:
- 함수 전체에 락이 걸림 (너무 큰 범위)
- 여러 스레드가 동시에 못 들어와서 성능 떨어짐

---

## 성능 개선 방법

### 방법 1: 진짜 필요한 부분만 잠구기

```kotlin
fun doWork(data: String) {
    val result = data.uppercase()  // 이건 락 필요 없음

    synchronized(this) {
        sharedList.add(result)     // 이 부분만 잠금
    }
}
```

---

### 방법 2: 더 똑똑한 라이브러리 를 써보자.

#### ReentrantLock
```kotlin
val lock = ReentrantLock()

lock.lock()
try {
    // 자원 사용
} finally {
    lock.unlock()
}
```

#### AtomicInteger (숫자에 특화된 락 없는 도구)

```kotlin
val counter = AtomicInteger(0)

fun increment() = counter.incrementAndGet()
```

#### ConcurrentHashMap

```kotlin
val map = ConcurrentHashMap<String, Int>()

map["user"] = 10
```

- 내부적으로 락을 잘게 쪼개서 처리하니, 훨씬 빠르다.

---

## 언제 어떤 걸 써야 할까?

| 상황                         | 추천 도구                              |
|----------------------------|----------------------------------------|
| 단순하게 잠그고 싶을 때         | `@Synchronized`, `synchronized`       |
| 공유 맵/리스트를 쓸 때          | `ConcurrentHashMap`, `CopyOnWrite...` |
| 락을 세밀하게 제어하고 싶을 때   | `ReentrantLock`, `ReadWriteLock`      |
| 숫자나 불린 값만 바꿀 때         | `AtomicInteger`, `AtomicBoolean`      |

---

## 정리

- 자바는 **객체마다 모니터**라는 락+조건 기능을 기본 제공한다.
- `synchronized`는 그 모니터를 잠그는 문법이다.
- 더 빠르고 유연하게 하고 싶다면, **concurrent 도구들**을 활용