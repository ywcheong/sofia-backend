# sofia-backend

version: `26b.04.01`

Sofia 2026 시스템의 백엔드 코드입니다.

프로젝트를 구동시키기 위해서는 데이터베이스 연결을 위해 다음 환경변수가 필요합니다.

```bash
SOFIA_DATASOURCE_URL=
SOFIA_DATASOURCE_PASSWORD=
SOFIA_DATASOURCE_USERNAME=
```

최초의 데이터베이스 구성을 위해서는 [init.sql](init.sql)을 사용하세요. 해당 SQL 스크립트는 `./gradlew check`를 실행할 경우 자동으로 프로젝트 엔티티 스펙에 따라 업데이트됩니다. 테스트 코드 중 `SchemaExportTest`가 해당 SQL을 생성하는 것이며, 프로젝트 초기라 복잡도가 높지 않아 Flyway 대신 해당 테스트로 초기화 SQL 생성을 구현했습니다.

// todo: 아키텍처 다이어그램