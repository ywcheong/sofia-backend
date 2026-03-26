package ywcheong.sofia.user

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockMvc
import tools.jackson.databind.ObjectMapper
import ywcheong.sofia.config.RequestHelper
import ywcheong.sofia.config.TestScenarioHelper
import ywcheong.sofia.phase.SystemPhase
import ywcheong.sofia.task.TranslationTask

@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DisplayName("사용자 목록 조회")
class UserListQueryTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val helper: TestScenarioHelper,
) {
    private lateinit var requestHelper: RequestHelper
    private lateinit var adminInfo: TestScenarioHelper.AdminAuthInfo

    @BeforeEach
    fun cleanUp() {
        adminInfo = helper.setupTranslationScenario()
        requestHelper = RequestHelper(mockMvc, objectMapper, helper)
    }

    @Nested
    @DisplayName("페이지네이션")
    inner class PaginationTests {

        @Test
        fun `페이지네이션으로 사용자 목록을 조회하면 200을 반환한다`() {
            // given - 여러 사용자 생성
            helper.createActiveStudent("25-001", "사용자1")
            helper.createActiveStudent("25-002", "사용자2")
            helper.createActiveStudent("25-003", "사용자3")

            // when & then
            val result = requestHelper.get("/users?page=0&size=10", adminInfo.secretToken)
            requestHelper.assertOk(result)
        }

        @Test
        fun `첫 번째 페이지에는 요청한 개수만큼의 사용자가 반환된다`() {
            // given - 여러 사용자 생성
            helper.createActiveStudent("25-010", "사용자A")
            helper.createActiveStudent("25-011", "사용자B")

            // when & then
            val result = requestHelper.get("/users?page=0&size=1", adminInfo.secretToken)
            requestHelper.assertOk(result)
        }

        @Test
        fun `사용자 응답에 필요한 필드가 모두 포함된다`() {
            // given
            helper.createActiveStudent("25-020", "테스트사용자")

            // when & then
            val result = requestHelper.get("/users?page=0&size=10", adminInfo.secretToken)
            requestHelper.assertOk(result)
        }

        @Test
        fun `사용자 목록 조회 - 자수 필드 포함`() {
            // given - 사용자 생성 및 과제 완료
            val user = helper.createActiveStudent("25-100", "자수테스트")
            val task = helper.createTranslationTask(
                taskType = TranslationTask.TaskType.GAONNURI_POST,
                taskDescription = "테스트 과제",
                assignee = user
            )
            helper.setTaskCompletedAt(task.id, java.time.Instant.now(), 1000)

            // when & then
            val result = requestHelper.get("/users?page=0&size=10", adminInfo.secretToken)
            requestHelper.assertOk(result)
        }
    }

    @Nested
    @DisplayName("검색 및 필터링")
    inner class FilterTests {

        @Test
        fun `사용자 목록 조회 - 학번으로 검색`() {
            // given - 여러 사용자 생성
            helper.createActiveStudent("25-200", "김철수")
            helper.createActiveStudent("25-201", "이영희")
            helper.createActiveStudent("26-001", "박민수")

            // when & then - 학번으로 검색
            val result = requestHelper.get("/users?page=0&size=10&search=25-20", adminInfo.secretToken)
            requestHelper.assertOk(result)
        }

        @Test
        fun `사용자 목록 조회 - 이름으로 검색`() {
            // given - 여러 사용자 생성
            helper.createActiveStudent("25-210", "김철수")
            helper.createActiveStudent("25-211", "이철수")
            helper.createActiveStudent("25-212", "박민수")

            // when & then - 이름으로 검색
            val result = requestHelper.get("/users?page=0&size=10&search=철수", adminInfo.secretToken)
            requestHelper.assertOk(result)
        }

        @Test
        fun `사용자 목록 조회 - 권한으로 필터링`() {
            // given - 학생과 관리자 생성
            helper.createActiveStudent("25-220", "일반학생")
            helper.createAdminAndGetToken("admin-filter", "관리자학생")

            // when & then - 관리자만 조회
            val result = requestHelper.get("/users?page=0&size=10&role=ADMIN", adminInfo.secretToken)
            requestHelper.assertOk(result)
        }

        @Test
        fun `사용자 목록 조회 - 학생만 필터링`() {
            // given - 학생과 관리자 생성
            helper.createActiveStudent("25-230", "학생A")
            helper.createActiveStudent("25-231", "학생B")

            // when & then - 학생만 조회
            val result = requestHelper.get("/users?page=0&size=10&role=STUDENT", adminInfo.secretToken)
            requestHelper.assertOk(result)
        }

        @Test
        fun `사용자 목록 조회 - 휴식 상태로 필터링`() {
            // given - 활성 사용자와 휴식 사용자 생성
            helper.createActiveStudent("25-240", "활성사용자")
            val restingUser = helper.createActiveStudent("25-241", "휴식사용자")
            helper.setUserResting(restingUser.id, true)

            // when & then - 휴식 중인 사용자만 조회
            val result = requestHelper.get("/users?page=0&size=10&rest=true", adminInfo.secretToken)
            requestHelper.assertOk(result)
        }

        @Test
        fun `사용자 목록 조회 - 활성 상태로 필터링`() {
            // given - 활성 사용자와 휴식 사용자 생성
            helper.createActiveStudent("25-250", "활성A")
            helper.createActiveStudent("25-251", "활성B")

            // when & then - 활성 사용자만 조회
            val result = requestHelper.get("/users?page=0&size=10&rest=false", adminInfo.secretToken)
            requestHelper.assertOk(result)
        }

        @Test
        fun `사용자 목록 조회 - 복합 조건 필터링`() {
            // given - 다양한 조건의 사용자 생성
            helper.createActiveStudent("25-260", "김학생")
            val restingStudent = helper.createActiveStudent("25-261", "이학생")
            helper.setUserResting(restingStudent.id, true)

            // when & then - 활성 상태 + 학생 권한으로 필터링
            val result = requestHelper.get("/users?page=0&size=10&role=STUDENT&rest=false", adminInfo.secretToken)
            requestHelper.assertOk(result)
        }
    }

    @Nested
    @DisplayName("정렬")
    inner class SortTests {

        @Test
        fun `정렬 파라미터 없이 조회하면 기본 동작을 유지한다`() {
            // given - 여러 사용자 생성
            helper.createActiveStudent("25-300", "가나다")
            helper.createActiveStudent("25-301", "라마바")

            // when & then - 정렬 파라미터 없이 조회
            val result = requestHelper.get("/users?page=0&size=10", adminInfo.secretToken)
            requestHelper.assertOk(result)
        }

        @Test
        fun `학번 오름차순 정렬로 조회한다`() {
            // given - 역순으로 생성해도 정렬되어야 함
            helper.createActiveStudent("25-320", "사용자C")
            helper.createActiveStudent("25-310", "사용자B")
            helper.createActiveStudent("25-300", "사용자A")

            // when & then - 학번 오름차순 정렬
            val result = requestHelper.get("/users?page=0&size=10&sortField=studentNumber&sortDirection=ASC", adminInfo.secretToken)
            requestHelper.assertOk(result)
        }

        @Test
        fun `학번 내림차순 정렬로 조회한다`() {
            // given
            helper.createActiveStudent("25-330", "사용자A")
            helper.createActiveStudent("25-340", "사용자B")
            helper.createActiveStudent("25-350", "사용자C")

            // when & then - 학번 내림차순 정렬
            val result = requestHelper.get("/users?page=0&size=10&sortField=studentNumber&sortDirection=DESC", adminInfo.secretToken)
            requestHelper.assertOk(result)
        }

        @Test
        fun `이름 오름차순 정렬로 조회한다`() {
            // given - 가나다순이 아닌 이름으로 생성
            helper.createActiveStudent("25-400", "박민수")
            helper.createActiveStudent("25-401", "김철수")
            helper.createActiveStudent("25-402", "이영희")

            // when & then - 이름 오름차순 정렬
            val result = requestHelper.get("/users?page=0&size=10&sortField=studentName&sortDirection=ASC", adminInfo.secretToken)
            requestHelper.assertOk(result)
        }

        @Test
        fun `이름 내림차순 정렬로 조회한다`() {
            // given
            helper.createActiveStudent("25-410", "가가가")
            helper.createActiveStudent("25-411", "나나나")
            helper.createActiveStudent("25-412", "다다다")

            // when & then - 이름 내림차순 정렬
            val result = requestHelper.get("/users?page=0&size=10&sortField=studentName&sortDirection=DESC", adminInfo.secretToken)
            requestHelper.assertOk(result)
        }

        @Test
        fun `경고 횟수 오름차순 정렬로 조회한다`() {
            // given
            helper.createActiveStudent("25-420", "사용자A")
            helper.createActiveStudent("25-421", "사용자B")
            helper.createActiveStudent("25-422", "사용자C")

            // when & then - 경고 횟수 오름차순 정렬
            val result = requestHelper.get("/users?page=0&size=10&sortField=warningCount&sortDirection=ASC", adminInfo.secretToken)
            requestHelper.assertOk(result)
        }

        @Test
        fun `경고 횟수 내림차순 정렬로 조회한다`() {
            // given
            helper.createActiveStudent("25-430", "사용자A")
            helper.createActiveStudent("25-431", "사용자B")

            // when & then - 경고 횟수 내림차순 정렬
            val result = requestHelper.get("/users?page=0&size=10&sortField=warningCount&sortDirection=DESC", adminInfo.secretToken)
            requestHelper.assertOk(result)
        }

        @Test
        fun `권한 오름차순 정렬로 조회한다`() {
            // given - 관리자와 학생 혼재
            helper.createActiveStudent("25-440", "학생A")
            helper.createActiveStudent("25-441", "학생B")
            helper.createAdminAndGetToken("admin-sort-asc", "관리자A")

            // when & then - 권한 오름차순 정렬 (ADMIN < STUDENT)
            val result = requestHelper.get("/users?page=0&size=10&sortField=role&sortDirection=ASC", adminInfo.secretToken)
            requestHelper.assertOk(result)
        }

        @Test
        fun `권한 내림차순 정렬로 조회한다`() {
            // given - 관리자와 학생 혼재
            helper.createActiveStudent("25-450", "학생C")
            helper.createActiveStudent("25-451", "학생D")
            helper.createAdminAndGetToken("admin-sort-desc", "관리자B")

            // when & then - 권한 내림차순 정렬 (STUDENT > ADMIN)
            val result = requestHelper.get("/users?page=0&size=10&sortField=role&sortDirection=DESC", adminInfo.secretToken)
            requestHelper.assertOk(result)
        }

        @Test
        fun `휴식 상태 오름차순 정렬로 조회한다`() {
            // given - 활성 사용자와 휴식 사용자 혼재
            helper.createActiveStudent("25-460", "활성A")
            helper.createActiveStudent("25-461", "활성B")
            val restingUser = helper.createActiveStudent("25-462", "휴식A")
            helper.setUserResting(restingUser.id, true)

            // when & then - 휴식 상태 오름차순 정렬 (false=활성 < true=휴식)
            val result = requestHelper.get("/users?page=0&size=10&sortField=rest&sortDirection=ASC", adminInfo.secretToken)
            requestHelper.assertOk(result)
        }

        @Test
        fun `휴식 상태 내림차순 정렬로 조회한다`() {
            // given - 활성 사용자와 휴식 사용자 혼재
            helper.createActiveStudent("25-470", "활성C")
            helper.createActiveStudent("25-471", "활성D")
            val restingUser = helper.createActiveStudent("25-472", "휴식B")
            helper.setUserResting(restingUser.id, true)

            // when & then - 휴식 상태 내림차순 정렬 (true=휴식 > false=활성)
            val result = requestHelper.get("/users?page=0&size=10&sortField=rest&sortDirection=DESC", adminInfo.secretToken)
            requestHelper.assertOk(result)
        }

        @Test
        fun `총 자수로 오름차순 정렬한다 - completedCharCount와 adjustedCharCount 합산`() {
            // given - 3명의 사용자 생성, 각각 다른 자수 부여
            val userA = helper.createActiveStudent("25-500", "사용자A")
            val taskA = helper.createTranslationTask(
                taskType = TranslationTask.TaskType.GAONNURI_POST,
                taskDescription = "과제A",
                assignee = userA
            )
            helper.setTaskCompletedAt(taskA.id, java.time.Instant.now(), 1000)
            adjustCharCount(userA.id, 500)

            val userB = helper.createActiveStudent("25-501", "사용자B")
            val taskB = helper.createTranslationTask(
                taskType = TranslationTask.TaskType.GAONNURI_POST,
                taskDescription = "과제B",
                assignee = userB
            )
            helper.setTaskCompletedAt(taskB.id, java.time.Instant.now(), 2000)

            val userC = helper.createActiveStudent("25-502", "사용자C")
            val taskC = helper.createTranslationTask(
                taskType = TranslationTask.TaskType.GAONNURI_POST,
                taskDescription = "과제C",
                assignee = userC
            )
            helper.setTaskCompletedAt(taskC.id, java.time.Instant.now(), 500)
            adjustCharCount(userC.id, 300)

            // when & then - 총 자수 오름차순 정렬: C(800) -> A(1500) -> B(2000)
            val result = requestHelper.get("/users?page=0&size=10&role=STUDENT&sortField=totalCharCount&sortDirection=ASC", adminInfo.secretToken)
            requestHelper.assertOk(result)
        }

        @Test
        fun `총 자수로 내림차순 정렬한다 - completedCharCount와 adjustedCharCount 합산`() {
            // given - 3명의 사용자 생성, 각각 다른 자수 부여
            val userA = helper.createActiveStudent("25-510", "사용자A")
            val taskA = helper.createTranslationTask(
                taskType = TranslationTask.TaskType.GAONNURI_POST,
                taskDescription = "과제A",
                assignee = userA
            )
            helper.setTaskCompletedAt(taskA.id, java.time.Instant.now(), 1000)
            adjustCharCount(userA.id, 500)

            val userB = helper.createActiveStudent("25-511", "사용자B")
            val taskB = helper.createTranslationTask(
                taskType = TranslationTask.TaskType.GAONNURI_POST,
                taskDescription = "과제B",
                assignee = userB
            )
            helper.setTaskCompletedAt(taskB.id, java.time.Instant.now(), 2000)

            val userC = helper.createActiveStudent("25-512", "사용자C")
            val taskC = helper.createTranslationTask(
                taskType = TranslationTask.TaskType.GAONNURI_POST,
                taskDescription = "과제C",
                assignee = userC
            )
            helper.setTaskCompletedAt(taskC.id, java.time.Instant.now(), 500)
            adjustCharCount(userC.id, 300)

            // when & then - 총 자수 내림차순 정렬: B(2000) -> A(1500) -> C(800)
            val result = requestHelper.get("/users?page=0&size=10&role=STUDENT&sortField=totalCharCount&sortDirection=DESC", adminInfo.secretToken)
            requestHelper.assertOk(result)
        }

        @Test
        fun `총 자수 정렬 - 보정 자수만 있는 사용자도 올바르게 정렬된다`() {
            // given - 완료 과제는 없지만 보정 자수만 있는 사용자
            val userD = helper.createActiveStudent("25-520", "사용자D")
            adjustCharCount(userD.id, 100)

            val userE = helper.createActiveStudent("25-521", "사용자E")
            adjustCharCount(userE.id, 500)

            val userF = helper.createActiveStudent("25-522", "사용자F")
            adjustCharCount(userF.id, 50)

            // when & then - 총 자수 오름차순 정렬: F(50) -> D(100) -> E(500)
            val result = requestHelper.get("/users?page=0&size=10&role=STUDENT&sortField=totalCharCount&sortDirection=ASC", adminInfo.secretToken)
            requestHelper.assertOk(result)
        }

        @Test
        fun `지원하지 않는 필드로 정렬 요청 시 400 에러를 반환한다`() {
            // given
            helper.createActiveStudent("25-600", "사용자")

            // when & then - 지원하지 않는 필드로 정렬
            val result = requestHelper.get("/users?page=0&size=10&sortField=invalidField&sortDirection=ASC", adminInfo.secretToken)
            requestHelper.assertBadRequest(result)
        }

        @Test
        fun `정렬 필드 없이 방향만 지정하면 400 에러를 반환한다`() {
            // given
            helper.createActiveStudent("25-601", "사용자")

            // when & then - 정렬 필드 없이 방향만 지정
            val result = requestHelper.get("/users?page=0&size=10&sortDirection=DESC", adminInfo.secretToken)
            requestHelper.assertBadRequest(result)
        }

        @Test
        fun `정렬과 조건 검색을 조합한다 - 학생 중 총 자수 내림차순`() {
            // given - 학생과 관리자 혼재, 각각 다른 자수
            val studentA = helper.createActiveStudent("25-700", "학생A")
            val taskA = helper.createTranslationTask(
                taskType = TranslationTask.TaskType.GAONNURI_POST,
                taskDescription = "과제A",
                assignee = studentA
            )
            helper.setTaskCompletedAt(taskA.id, java.time.Instant.now(), 3000)

            val studentB = helper.createActiveStudent("25-701", "학생B")
            val taskB = helper.createTranslationTask(
                taskType = TranslationTask.TaskType.GAONNURI_POST,
                taskDescription = "과제B",
                assignee = studentB
            )
            helper.setTaskCompletedAt(taskB.id, java.time.Instant.now(), 1000)

            helper.createAdminAndGetToken("admin-sort", "관리자B")

            // when & then - 학생만 필터링 + 총 자수 내림차순 정렬
            val result = requestHelper.get("/users?page=0&size=10&role=STUDENT&sortField=totalCharCount&sortDirection=DESC", adminInfo.secretToken)
            requestHelper.assertOk(result)
        }

        @Test
        fun `정렬과 휴식 상태 필터를 조합한다 - 활성 사용자 중 이름 오름차순`() {
            // given - 활성 사용자와 휴식 사용자 혼재
            helper.createActiveStudent("25-710", "활성C")
            helper.createActiveStudent("25-711", "활성A")
            helper.createActiveStudent("25-712", "활성B")
            val restingUser = helper.createActiveStudent("25-713", "휴식자")
            helper.setUserResting(restingUser.id, true)

            // when & then - 활성 사용자만 + 이름 오름차순 정렬
            val result = requestHelper.get("/users?page=0&size=10&rest=false&sortField=studentName&sortDirection=ASC", adminInfo.secretToken)
            requestHelper.assertOk(result)
        }
    }

    // 헬퍼 메서드: 보정 자수 조정 (API 사용)
    private fun adjustCharCount(userId: java.util.UUID, amount: Int) {
        val request = mapOf("amount" to amount)
        val result = requestHelper.post("/users/$userId/adjust-char-count", request, adminInfo.secretToken)
        requestHelper.assertOk(result)
    }
}
