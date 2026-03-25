## 프로젝트 규칙

- Simple is the best. 빠른 개발을 위해 3-layered 아키텍처 선택.

## 도구사용 규칙

- `./gradlew` 설정에서 출력을 줄이는 옵션이 켜져 있어, 출력이 안 나올 수도 있다. 이때는 exit 코드가 0이면 성공으로 간주한다.

## 코드작성 규칙

- 모든 엔티티의 ID는 DBMS의 Auto Generated ID을 금지하며, 대신 UUID를 사용해야 함.
- Logging은 `import io.github.oshai.kotlinlogging.KotlinLogging` 이후, 클래스 내부 필드로 `private val logger = KotlinLogging.logger {}` 선언 후 사용한다. 클래스 외부에는 절대 로거를 선언하지 않는다.
- 예외가 사용자의 잘못으로 인한 것이면 `BusinessException`을 사용하고, message에 사용자에게 보여줄 메시지 작성 (400 Bad Request로 매핑됨)
- 예외가 시스템 장애로 인한 것이면 `BusinessException`을 제외한 어떤 Exception을 사용해도 무방하며, 이때 message는 자동 로깅됨 (500 Internal Server Error로 매핑됨)
- 만약 논리적으로 null일 수 없는 값이 nullable할 경우 `value!!` 사용을 금지하고, 대신 `checkNotNull(value){ "왜 value가 null이 아닐 수밖에 없는지" }` 설명할 것.
- `when` expression 내부 등 nesting이 깊어질 경우, 가독성을 위해 별도의 private method로 분리할 것.
- 매직 넘버나 환경별로 다를 수 있는 설정값은 `@ConfigurationProperties`를 통해 외부화하고 `application.yml`에 등록할 것. 절대로 코드에 기본값을 정의하지 말 것 - 설정이 주입되지 않은 경우를 탐지하기 어려워지기 때문.
- Service 계층의 함수는 파라미터가 2개 이상일 경우 Command 객체로 묶고, 입력 검증은 Command의 `init` 블록에서 수행할 것. 맥락에 따라 적절한 예외(사용자 입력 오류면 `BusinessException`)를 던질 것.
- JPA 엔티티 선언 시 Kotlin 이슈로 인해 다음 패턴을 따를 것: 생성자에는 파라미터만 선언하고( `val`/`var` 없이), 본문에 모든 영속 프로퍼티를 `var`로 선언하며, 외부 가변을 막으려면 `protected set`을 사용할 것.
- `@AvailableCondition`은 Controller 메서드의 접근 제어를 담당하며, `phases`(허용 페이즈)와 `permissions`(허용 권한)를 OR 조건으로 검사한다. 둘 중 하나라도 충족하면 접근 허용.
  - `phases`: 현재 시스템 페이즈가 배열에 포함되면 통과, `permissions`: 현재 사용자가 배열 내 권한 중 하나를 보유하면 통과.
  - 빈 배열은 검증하지 않는다는 뜻으로, 해당 조건이 검사에서 제외된다.
  - 예: `@AvailableCondition(phases = [SystemPhase.RECRUITMENT, SystemPhase.TRANSLATION], permissions = [SofiaPermission.ADMIN])`
- 모든 Controller의 public 메서드는 반드시 `@AvailableCondition` 어노테이션을 부착해야 함. 이는 ArchUnit 테스트로 강제됨.
- Controller에 `@CurrentUser user: SofiaUser` 파라미터를 추가하면 웹에서 현재 인증된 사용자가 주입됨.
- KakaoSkill 구현 시 `KakaoSkill<ACT>`를 상속한 클래스를 만들어 @Service로 등록하고 `handleInternal()`만 구현하면 나머지는 자동으로 처리됨.
- 컨트롤러 및 엔티티에는 `is~`, `get~`으로 시작하는 필드/프로퍼티를 절대 선언 금지. JavaBeans 규약과 Kotlin 네이밍 컨벤션에 심각한 불일치가 있기 때문임.

## 테스트작성 규칙

- 통합 테스트는 MockMVC로 요청을 생성해서 검사하며, 테스트 시나리오 설정은 반드시 `TestScenarioHelper`를 통해서만 수행한다. 내부 리포지토리에 직접 접근하는 등 실제 사용자가 사용할 수 없는 방식으로는 테스트하지 않는다.
- 단위 테스트는 별도의 요청이 없을 경우 DTO 형식 검증 등의 제한된 경우에만 사용한다.
- 테스트 메서드명은 백틱으로 감싼 한국어를 활용하며, 가급적 비즈니스 의미가 있는 메서드명으로 짓는다. 가능하다면 언더바 대신 공백으로 짓는다.
- 테스트는 공개 API를 통해서만 검증하며, 구현 세부사항을 직접 검증하지 않는다.
- ObjectMapper 사용 시 `tools.jackson.databind.ObjectMapper` (Jackson 3.x)를 import할 것. Spring Boot 4.x는 Jackson 3.x를 기본으로 사용하므로 별도 설정 없이 주입 가능함. `com.fasterxml.jackson.databind.ObjectMapper` (Jackson 2.x)나 testcontainers shaded 버전을 import하지 않도록 주의.
- 테스트 클래스명은 `{Domain}Test` 형식을 따르며, `@DisplayName("{도메인 한국어명}")`을 추가한다. (`XxxControllerTest` 지양)
- 메서드 레벨 `@DisplayName`은 `"{HTTP_METHOD} {endpoint} - {비즈니스 의미}"` 형식을 따른다.
- 내부 클래스(`inner class`)명은 비즈니스 행위를 기반으로 작명한다. (`CreateTests`, `FindAllTests` 등 CRUD 용어 지양)
- 테스트에서는 인증, 페이즈 조건은 검증하지 않음 - 횡단 관심이므로 별도의 인증테스트, 페이즈테스트에서 모두 몰아서 검사하기 때문
- 테스트 환경에서 외부 리소스(이메일, 외부 API 등)가 실제로 호출되지 않도록, `build.gradle.kts`의 `tasks.withType<Test>` 블록에서 `systemProperty()`로 설정값을 주입하여 비활성화한다. 별도의 테스트용 설정 파일을 생성하지 않는다.

## 버전관리 규칙

(공란)

## 문서화 규칙

- `docs/feature/{feature}.json`, `docs/tech/{tech}.json`에 기능/기술별 요약 정보를 보관할 것.
- 기능 또는 기술을 사용하거나 수정하기 전, 반드시 대응하는 문서를 읽을 것.
- 기능 또는 기술에 변경 발생 시, 반드시 대응하는 문서도 수정할 것.
