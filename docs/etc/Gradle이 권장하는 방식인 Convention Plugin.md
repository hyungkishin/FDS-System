# 빌드 로직을 모듈로 승격시켜서 관리
보통 build-logic이라는 모듈을 만들고 그 안에서 공통 규칙을 가진 플러그인을 정의한다고 한다.  
프로젝트 셋팅 초기에 buildSrc 내부 Jacoco exclude 설정 파일을 관리하기 위해 추가했었다.  
root gradle.kts 가 점점 비대해 지니, 관리하기가 힘들어 build-logic 을 알아보게 되었다.

구조 
```text
root
 ├── build.gradle.kts (루트 빌드)
 ├── settings.gradle.kts
 ├── build-logic
 │    ├── build.gradle.kts
 │    └── src/main/kotlin
 │         └── transentia
 │               ├── CodeCoverageConventionPlugin.kt
 │               ├── KotlinLibraryConventionPlugin.kt
 │               ├── Modules.kt
 │               ├── RootConventionsPlugin.kt
 │               ├── SpringBootAppConventionPlugin.kt
 │               ├── SpringJpaConventionPlugin.kt
 │               └── SpringLibraryConventionPlugin.kt
 └── ...

```

| 파일명                                  | 역할                  | 주요 기능                                                                                                                                              |
| ------------------------------------ | ------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| **CodeCoverageConventionPlugin.kt**  | 코드 커버리지 규칙          | - `jacoco` 플러그인 자동 적용<br>- 공통 exclude 패턴 정의<br>- 리포트 생성/검증 태스크 설정<br>- CI 환경에서 coverage 검증 자동 포함                                                   |
| **KotlinLibraryConventionPlugin.kt** | 순수 Kotlin 모듈 규칙     | - `kotlin("jvm")` 플러그인 적용<br>- JVM Toolchain 21 적용<br>- Kotlin 컴파일러 옵션(jvmTarget, strict nullability) 지정                                           |
| **Modules.kt**                       | 모듈 상수 관리            | - 모듈 이름을 상수화하여 재사용<br>- RootConventionsPlugin 등에서 set 정의 시 중복 제거 및 오타 방지                                                                           |
| **RootConventionsPlugin.kt**         | 전체 프로젝트 공통 규칙       | - 모든 `subprojects`에 대해 규칙 적용<br>- 모듈 유형별 적절한 플러그인 연결<br>- JPA/커버리지 모듈에 추가 플러그인 적용                                                                  |
| **SpringBootAppConventionPlugin.kt** | Spring Boot 실행 앱 규칙 | - `org.springframework.boot`, `dependency-management` 플러그인 적용<br>- `kotlin-jvm`, `plugin.spring`, `plugin.allopen` 자동 적용<br>- Boot 모듈 특수 빌드 처리     |
| **SpringJpaConventionPlugin.kt**     | JPA 모듈 규칙           | - `plugin.jpa` 플러그인 적용<br>- `spring-boot-starter-data-jpa`, `postgresql` 의존성 자동 추가                                                                 |
| **SpringLibraryConventionPlugin.kt** | Spring 기반 라이브러리 규칙  | - `kotlin-jvm`, `plugin.spring`, `plugin.allopen` 적용<br>- Spring Boot BOM(platform dependency) 적용<br>- 공통 의존성(`kotlin-reflect`, `starter-test`) 추가 |


## buildSrc란?

Gradle의 특별한 디렉토리로,     
프로젝트 루트에 buildSrc 디렉토리를 만들면, 자동으로 별도의 빌드처럼 컴파일되고 classpath에 포함된다.  

만든 Kotlin/Java 플러그인이나 확장 코드는 모든 모듈의 build.gradle(.kts)에서 바로 쓸 수 있다.  

### 장점
- 설정이 간단하다 (그냥 디렉토리만 만들면 됨)
- 자동으로 classpath에 포함된다.

### 단점
- 숨겨진 매직이라서 IDE나 새 팀원 입장에서 "이게 왜 되는 거지?" 라는 혼동이 생긴다.
- 여러 개의 root build에서 공유하기 어렵다. (buildSrc는 프로젝트 하나에 종속적)
- 점점 규모가 커지면 빌드 성능에 영향을 줄 수 있다.

## build-logic (included build)란?

includeBuild("build-logic")로 연결하는 방식으로, Gradle이 Composite Build로 취급한다.  

사실상 buildSrc와 같은 역할을 하지만  
명시적으로 root settings.gradle.kts에 include하기 때문에 보이는 매커니즘임.

여러 프로젝트 간에 공유 가능. (예: mono-repo 여러 개, 혹은 팀 공통 conventions 묶음을 관리할 때)

## 차이 정리
| 구분      | buildSrc              | build-logic (included build)        |
| ------- | --------------------- | ----------------------------------- |
| 선언 방식   | 디렉토리만 만들면 자동 인식       | `settings.gradle.kts`에서 include     |
| 가시성     | 암묵적 (매직 같음)           | 명시적 (settings에서 확인 가능)              |
| IDE 친화도 | 가끔 캐시 꼬임, 버전 카탈로그 못 씀 | 독립 Gradle project라 IDE 안정적          |
| 공유성     | 프로젝트 내부 한정            | 여러 프로젝트에서 재사용 가능                    |
| 확장성     | 단순 플러그인 정도 관리에 적합     | conventions, dependency 관리 등 대규모 적합 |


## 왜 쓰는가..
보기가 좋고 관리 point 가 보기좋게 명확해 지니 좋긴한데, 이게 최선인가 라는 의문은 솔직히 든다.

- 투명성: 플러그인/규칙이 어디서 오는지 명확히 보임.
- 유연성: 버전 카탈로그나 별도 dependency 선언 가능 ( buildSrc 는 이게 불가 하다 )
- 재사용성: 여러 repo/서비스에 공통 conventions를 공유할 수 있음.

> Gradle 팀도 권장: Gradle 공식 문서에서 buildSrc보다 build-logic composite build 사용을 장려하고 있음.