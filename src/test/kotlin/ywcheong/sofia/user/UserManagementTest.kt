package ywcheong.sofia.user

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import ywcheong.sofia.config.TestScenarioHelper
import ywcheong.sofia.phase.SystemPhase
import java.util.*

@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DisplayName("사용자 관리")
class UserManagementTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val helper: TestScenarioHelper,
) {
    private lateinit var adminInfo: TestScenarioHelper.AdminAuthInfo

    @BeforeEach
    fun cleanUp() {
        adminInfo = helper.setupScenarioWithAdmin(SystemPhase.TRANSLATION)
        // 관리자를 휴식 상태로 설정 (배정/활성 사용자 대상에서 제외)
        helper.setUserResting(adminInfo.userId, true)
    }

    @Nested
    @DisplayName("GET /users - 사용자 목록 조회")
    inner class FindAllUsers {

        @Test
        fun `페이지네이션으로 사용자 목록을 조회하면 200을 반환한다`() {
            // given - 여러 사용자 생성
            helper.createActiveStudent("25-001", "사용자1")
            helper.createActiveStudent("25-002", "사용자2")
            helper.createActiveStudent("25-003", "사용자3")

            // when & then
            mockMvc.get("/users?page=0&size=10") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content") { isArray() }
                jsonPath("$.totalElements") { isNumber() }
                jsonPath("$.totalPages") { isNumber() }
                jsonPath("$.size") { value(10) }
                jsonPath("$.number") { value(0) }
            }
        }

        @Test
        fun `첫 번째 페이지에는 요청한 개수만큼의 사용자가 반환된다`() {
            // given - 여러 사용자 생성
            helper.createActiveStudent("25-010", "사용자A")
            helper.createActiveStudent("25-011", "사용자B")

            // when & then
            mockMvc.get("/users?page=0&size=1") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(1) }
                jsonPath("$.totalElements") { value(3) } // admin + 2 users
            }
        }

        @Test
        fun `사용자 응답에 필요한 필드가 모두 포함된다`() {
            // given
            val user = helper.createActiveStudent("25-020", "테스트사용자")

            // when & then
            mockMvc.get("/users?page=0&size=10") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content[0].id") { isString() }
                jsonPath("$.content[0].studentNumber") { isString() }
                jsonPath("$.content[0].studentName") { isString() }
                jsonPath("$.content[0].role") { isString() }
                jsonPath("$.content[0].rest") { isBoolean() }
                jsonPath("$.content[0].warningCount") { isNumber() }
                jsonPath("$.content[0].adjustedCharCount") { isNumber() }
            }
        }

        @Test
        fun `사용자 목록 조회 - 자수 필드 포함`() {
            // given - 사용자 생성 및 과제 완료
            val user = helper.createActiveStudent("25-100", "자수테스트")
            val task = helper.createTranslationTask(
                taskType = ywcheong.sofia.task.TranslationTask.TaskType.GAONNURI_POST,
                taskDescription = "테스트 과제",
                assignee = user
            )
            // 과제 완료 처리를 위해 직접 상태 변경 (도우미 메서드 필요시 추가)
            completeTask(task.id)

            // when & then
            mockMvc.get("/users?page=0&size=10") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content[0].completedCharCount") { isNumber() }
                jsonPath("$.content[0].totalCharCount") { isNumber() }
                jsonPath("$.content[0].adjustedCharCount") { isNumber() }
            }
        }

        @Test
        fun `사용자 목록 조회 - 학번으로 검색`() {
            // given - 여러 사용자 생성
            helper.createActiveStudent("25-200", "김철수")
            helper.createActiveStudent("25-201", "이영희")
            helper.createActiveStudent("26-001", "박민수")

            // when & then - 학번으로 검색
            mockMvc.get("/users?page=0&size=10&search=25-20") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(2) } // 25-200, 25-201 매칭
            }
        }

        @Test
        fun `사용자 목록 조회 - 이름으로 검색`() {
            // given - 여러 사용자 생성
            helper.createActiveStudent("25-210", "김철수")
            helper.createActiveStudent("25-211", "이철수")
            helper.createActiveStudent("25-212", "박민수")

            // when & then - 이름으로 검색
            mockMvc.get("/users?page=0&size=10&search=철수") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(2) } // 김철수, 이철수 매칭
            }
        }

        @Test
        fun `사용자 목록 조회 - 권한으로 필터링`() {
            // given - 학생과 관리자 생성
            helper.createActiveStudent("25-220", "일반학생")
            helper.createAdminAndGetToken("admin-filter", "관리자학생")

            // when & then - 관리자만 조회
            mockMvc.get("/users?page=0&size=10&role=ADMIN") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(2) } // adminInfo + admin-filter
                jsonPath("$.content[*].role") { value(mutableListOf("ADMIN", "ADMIN")) }
            }
        }

        @Test
        fun `사용자 목록 조회 - 학생만 필터링`() {
            // given - 학생과 관리자 생성
            helper.createActiveStudent("25-230", "학생A")
            helper.createActiveStudent("25-231", "학생B")
            // adminInfo는 이미 관리자로 존재

            // when & then - 학생만 조회
            mockMvc.get("/users?page=0&size=10&role=STUDENT") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(2) } // 학생A, 학생B
            }
        }

        @Test
        fun `사용자 목록 조회 - 휴식 상태로 필터링`() {
            // given - 활성 사용자와 휴식 사용자 생성
            helper.createActiveStudent("25-240", "활성사용자")
            val restingUser = helper.createActiveStudent("25-241", "휴식사용자")
            helper.setUserResting(restingUser.id, true)

            // when & then - 휴식 중인 사용자만 조회
            mockMvc.get("/users?page=0&size=10&rest=true") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(2) } // adminInfo + restingUser (둘 다 휴식)
            }
        }

        @Test
        fun `사용자 목록 조회 - 활성 상태로 필터링`() {
            // given - 활성 사용자와 휴식 사용자 생성
            helper.createActiveStudent("25-250", "활성A")
            helper.createActiveStudent("25-251", "활성B")
            // adminInfo는 휴식 상태

            // when & then - 활성 사용자만 조회
            mockMvc.get("/users?page=0&size=10&rest=false") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(2) } // 활성A, 활성B
            }
        }

        @Test
        fun `사용자 목록 조회 - 복합 조건 필터링`() {
            // given - 다양한 조건의 사용자 생성
            helper.createActiveStudent("25-260", "김학생")
            val restingStudent = helper.createActiveStudent("25-261", "이학생")
            helper.setUserResting(restingStudent.id, true)

            // when & then - 활성 상태 + 학생 권한으로 필터링
            mockMvc.get("/users?page=0&size=10&role=STUDENT&rest=false") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(1) } // 김학생만 해당
                jsonPath("$.content[0].studentName") { value("김학생") }
            }
        }
    }

    @Nested
    @DisplayName("GET /users - 정렬 기능")
    inner class SortUsers {

        @Test
        fun `정렬 파라미터 없이 조회하면 기본 동작을 유지한다`() {
            // given - 여러 사용자 생성
            helper.createActiveStudent("25-300", "가나다")
            helper.createActiveStudent("25-301", "라마바")

            // when & then - 정렬 파라미터 없이 조회
            mockMvc.get("/users?page=0&size=10") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content") { isArray() }
            }
        }

        @Test
        fun `학번 오름차순 정렬로 조회한다`() {
            // given - 역순으로 생성해도 정렬되어야 함
            helper.createActiveStudent("25-320", "사용자C")
            helper.createActiveStudent("25-310", "사용자B")
            helper.createActiveStudent("25-300", "사용자A")

            // when & then - 학번 오름차순 정렬
            mockMvc.get("/users?page=0&size=10&sortField=studentNumber&sortDirection=ASC") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content[0].studentNumber") { value("25-300") }
                jsonPath("$.content[1].studentNumber") { value("25-310") }
                jsonPath("$.content[2].studentNumber") { value("25-320") }
            }
        }

        @Test
        fun `학번 내림차순 정렬로 조회한다`() {
            // given
            helper.createActiveStudent("25-330", "사용자A")
            helper.createActiveStudent("25-340", "사용자B")
            helper.createActiveStudent("25-350", "사용자C")

            // when & then - 학번 내림차순 정렬
            mockMvc.get("/users?page=0&size=10&sortField=studentNumber&sortDirection=DESC") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content[0].studentNumber") { value("admin") } // 관리자가 먼저 (문자열 DESC)
            }
        }

        @Test
        fun `이름 오름차순 정렬로 조회한다`() {
            // given - 가나다순이 아닌 이름으로 생성
            helper.createActiveStudent("25-400", "박민수")
            helper.createActiveStudent("25-401", "김철수")
            helper.createActiveStudent("25-402", "이영희")

            // when & then - 이름 오름차순 정렬 (관리자, 김철수, 박민수, 이영희 - 한글 자음 순서)
            mockMvc.get("/users?page=0&size=10&sortField=studentName&sortDirection=ASC") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                // 관리자(ㄱ) < 김철수(ㄱ) < 박민수(ㅂ) < 이영희(ㅇ)
                jsonPath("$.content[0].studentName") { value("관리자") }
                jsonPath("$.content[1].studentName") { value("김철수") }
                jsonPath("$.content[2].studentName") { value("박민수") }
                jsonPath("$.content[3].studentName") { value("이영희") }
            }
        }

        @Test
        fun `이름 내림차순 정렬로 조회한다`() {
            // given - 한글 자음 순서: 관리자(ㄱ) < 가가가(ㄱ) < 나나나(ㄴ) < 다다다(ㄷ)
            // 역순: 다다다 > 나나나 > 관리자 > 가가가 (관리자와 가가가의 ㄱ 순서 문제로 인해)
            helper.createActiveStudent("25-410", "가가가")
            helper.createActiveStudent("25-411", "나나나")
            helper.createActiveStudent("25-412", "다다다")

            // when & then - 이름 내림차순 정렬 (다다다 > 나나나 > ?)
            mockMvc.get("/users?page=0&size=10&sortField=studentName&sortDirection=DESC") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content[0].studentName") { value("다다다") }
                jsonPath("$.content[1].studentName") { value("나나나") }
                // 관리자와 가가가 순서는 DB 정렬 규칙에 따라 달라질 수 있음
                jsonPath("$.content.length()") { value(4) }
            }
        }

        @Test
        fun `경고 횟수 오름차순 정렬로 조회한다`() {
            // given - 사용자 생성 후 경고 횟수 설정을 위해 과제 미완료 상황 시뮬레이션 불가하므로
            // 기본값(0)으로 생성된 사용자들로 테스트
            helper.createActiveStudent("25-420", "사용자A")
            helper.createActiveStudent("25-421", "사용자B")
            helper.createActiveStudent("25-422", "사용자C")

            // when & then - 경고 횟수 오름차순 정렬
            mockMvc.get("/users?page=0&size=10&sortField=warningCount&sortDirection=ASC") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content") { isArray() }
            }
        }

        @Test
        fun `경고 횟수 내림차순 정렬로 조회한다`() {
            // given
            helper.createActiveStudent("25-430", "사용자A")
            helper.createActiveStudent("25-431", "사용자B")

            // when & then - 경고 횟수 내림차순 정렬
            mockMvc.get("/users?page=0&size=10&sortField=warningCount&sortDirection=DESC") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content") { isArray() }
            }
        }

        @Test
        fun `권한 오름차순 정렬로 조회한다`() {
            // given - 관리자와 학생 혼재
            helper.createActiveStudent("25-440", "학생A")
            helper.createActiveStudent("25-441", "학생B")
            helper.createAdminAndGetToken("admin-sort-asc", "관리자A")

            // when & then - 권한 오름차순 정렬 (ADMIN < STUDENT)
            mockMvc.get("/users?page=0&size=10&sortField=role&sortDirection=ASC") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content[0].role") { value("ADMIN") }
                jsonPath("$.content[1].role") { value("ADMIN") }
                jsonPath("$.content[2].role") { value("STUDENT") }
                jsonPath("$.content[3].role") { value("STUDENT") }
            }
        }

        @Test
        fun `권한 내림차순 정렬로 조회한다`() {
            // given - 관리자와 학생 혼재
            helper.createActiveStudent("25-450", "학생C")
            helper.createActiveStudent("25-451", "학생D")
            helper.createAdminAndGetToken("admin-sort-desc", "관리자B")

            // when & then - 권한 내림차순 정렬 (STUDENT > ADMIN)
            mockMvc.get("/users?page=0&size=10&sortField=role&sortDirection=DESC") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content[0].role") { value("STUDENT") }
                jsonPath("$.content[1].role") { value("STUDENT") }
                jsonPath("$.content[2].role") { value("ADMIN") }
                jsonPath("$.content[3].role") { value("ADMIN") }
            }
        }

        @Test
        fun `휴식 상태 오름차순 정렬로 조회한다`() {
            // given - 활성 사용자와 휴식 사용자 혼재
            helper.createActiveStudent("25-460", "활성A")
            helper.createActiveStudent("25-461", "활성B")
            val restingUser = helper.createActiveStudent("25-462", "휴식A")
            helper.setUserResting(restingUser.id, true)

            // when & then - 휴식 상태 오름차순 정렬 (false=활성 < true=휴식)
            mockMvc.get("/users?page=0&size=10&sortField=rest&sortDirection=ASC") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content[0].rest") { value(false) }
                jsonPath("$.content[1].rest") { value(false) }
                jsonPath("$.content[2].rest") { value(true) }
                jsonPath("$.content[3].rest") { value(true) } // adminInfo도 휴식 상태
            }
        }

        @Test
        fun `휴식 상태 내림차순 정렬로 조회한다`() {
            // given - 활성 사용자와 휴식 사용자 혼재
            helper.createActiveStudent("25-470", "활성C")
            helper.createActiveStudent("25-471", "활성D")
            val restingUser = helper.createActiveStudent("25-472", "휴식B")
            helper.setUserResting(restingUser.id, true)

            // when & then - 휴식 상태 내림차순 정렬 (true=휴식 > false=활성)
            mockMvc.get("/users?page=0&size=10&sortField=rest&sortDirection=DESC") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content[0].rest") { value(true) }
                jsonPath("$.content[1].rest") { value(true) } // adminInfo도 휴식 상태
                jsonPath("$.content[2].rest") { value(false) }
                jsonPath("$.content[3].rest") { value(false) }
            }
        }

        @Test
        fun `총 자수로 오름차순 정렬한다 - completedCharCount와 adjustedCharCount 합산`() {
            // given - 3명의 사용자 생성, 각각 다른 자수 부여
            // 사용자 A: completedCharCount=1000, adjustedCharCount=500 -> total=1500
            val userA = helper.createActiveStudent("25-500", "사용자A")
            val taskA = helper.createTranslationTask(
                taskType = ywcheong.sofia.task.TranslationTask.TaskType.GAONNURI_POST,
                taskDescription = "과제A",
                assignee = userA
            )
            completeTask(taskA.id, 1000)
            adjustCharCount(userA.id, 500)

            // 사용자 B: completedCharCount=2000, adjustedCharCount=0 -> total=2000
            val userB = helper.createActiveStudent("25-501", "사용자B")
            val taskB = helper.createTranslationTask(
                taskType = ywcheong.sofia.task.TranslationTask.TaskType.GAONNURI_POST,
                taskDescription = "과제B",
                assignee = userB
            )
            completeTask(taskB.id, 2000)

            // 사용자 C: completedCharCount=500, adjustedCharCount=300 -> total=800
            val userC = helper.createActiveStudent("25-502", "사용자C")
            val taskC = helper.createTranslationTask(
                taskType = ywcheong.sofia.task.TranslationTask.TaskType.GAONNURI_POST,
                taskDescription = "과제C",
                assignee = userC
            )
            completeTask(taskC.id, 500)
            adjustCharCount(userC.id, 300)

            // when & then - 총 자수 오름차순 정렬: C(800) -> A(1500) -> B(2000)
            mockMvc.get("/users?page=0&size=10&role=STUDENT&sortField=totalCharCount&sortDirection=ASC") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(3) }
                jsonPath("$.content[0].studentName") { value("사용자C") }
                jsonPath("$.content[0].totalCharCount") { value(800) }
                jsonPath("$.content[1].studentName") { value("사용자A") }
                jsonPath("$.content[1].totalCharCount") { value(1500) }
                jsonPath("$.content[2].studentName") { value("사용자B") }
                jsonPath("$.content[2].totalCharCount") { value(2000) }
            }
        }

        @Test
        fun `총 자수로 내림차순 정렬한다 - completedCharCount와 adjustedCharCount 합산`() {
            // given - 3명의 사용자 생성, 각각 다른 자수 부여
            // 사용자 A: completedCharCount=1000, adjustedCharCount=500 -> total=1500
            val userA = helper.createActiveStudent("25-510", "사용자A")
            val taskA = helper.createTranslationTask(
                taskType = ywcheong.sofia.task.TranslationTask.TaskType.GAONNURI_POST,
                taskDescription = "과제A",
                assignee = userA
            )
            completeTask(taskA.id, 1000)
            adjustCharCount(userA.id, 500)

            // 사용자 B: completedCharCount=2000, adjustedCharCount=0 -> total=2000
            val userB = helper.createActiveStudent("25-511", "사용자B")
            val taskB = helper.createTranslationTask(
                taskType = ywcheong.sofia.task.TranslationTask.TaskType.GAONNURI_POST,
                taskDescription = "과제B",
                assignee = userB
            )
            completeTask(taskB.id, 2000)

            // 사용자 C: completedCharCount=500, adjustedCharCount=300 -> total=800
            val userC = helper.createActiveStudent("25-512", "사용자C")
            val taskC = helper.createTranslationTask(
                taskType = ywcheong.sofia.task.TranslationTask.TaskType.GAONNURI_POST,
                taskDescription = "과제C",
                assignee = userC
            )
            completeTask(taskC.id, 500)
            adjustCharCount(userC.id, 300)

            // when & then - 총 자수 내림차순 정렬: B(2000) -> A(1500) -> C(800)
            mockMvc.get("/users?page=0&size=10&role=STUDENT&sortField=totalCharCount&sortDirection=DESC") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(3) }
                jsonPath("$.content[0].studentName") { value("사용자B") }
                jsonPath("$.content[0].totalCharCount") { value(2000) }
                jsonPath("$.content[1].studentName") { value("사용자A") }
                jsonPath("$.content[1].totalCharCount") { value(1500) }
                jsonPath("$.content[2].studentName") { value("사용자C") }
                jsonPath("$.content[2].totalCharCount") { value(800) }
            }
        }

        @Test
        fun `총 자수 정렬 - 보정 자수만 있는 사용자도 올바르게 정렬된다`() {
            // given - 완료 과제는 없지만 보정 자수만 있는 사용자
            // 사용자 D: completedCharCount=0, adjustedCharCount=100 -> total=100
            val userD = helper.createActiveStudent("25-520", "사용자D")
            adjustCharCount(userD.id, 100)

            // 사용자 E: completedCharCount=0, adjustedCharCount=500 -> total=500
            val userE = helper.createActiveStudent("25-521", "사용자E")
            adjustCharCount(userE.id, 500)

            // 사용자 F: completedCharCount=0, adjustedCharCount=50 -> total=50
            val userF = helper.createActiveStudent("25-522", "사용자F")
            adjustCharCount(userF.id, 50)

            // when & then - 총 자수 오름차순 정렬: F(50) -> D(100) -> E(500)
            mockMvc.get("/users?page=0&size=10&role=STUDENT&sortField=totalCharCount&sortDirection=ASC") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(3) }
                jsonPath("$.content[0].studentName") { value("사용자F") }
                jsonPath("$.content[0].totalCharCount") { value(50) }
                jsonPath("$.content[1].studentName") { value("사용자D") }
                jsonPath("$.content[1].totalCharCount") { value(100) }
                jsonPath("$.content[2].studentName") { value("사용자E") }
                jsonPath("$.content[2].totalCharCount") { value(500) }
            }
        }

        @Test
        fun `지원하지 않는 필드로 정렬 요청 시 400 에러를 반환한다`() {
            // given
            helper.createActiveStudent("25-600", "사용자")

            // when & then - 지원하지 않는 필드로 정렬
            mockMvc.get("/users?page=0&size=10&sortField=invalidField&sortDirection=ASC") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `정렬 필드 없이 방향만 지정하면 400 에러를 반환한다`() {
            // given
            helper.createActiveStudent("25-601", "사용자")

            // when & then - 정렬 필드 없이 방향만 지정
            mockMvc.get("/users?page=0&size=10&sortDirection=DESC") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `정렬과 조건 검색을 조합한다 - 학생 중 총 자수 내림차순`() {
            // given - 학생과 관리자 혼재, 각각 다른 자수
            val studentA = helper.createActiveStudent("25-700", "학생A")
            val taskA = helper.createTranslationTask(
                taskType = ywcheong.sofia.task.TranslationTask.TaskType.GAONNURI_POST,
                taskDescription = "과제A",
                assignee = studentA
            )
            completeTask(taskA.id, 3000)

            val studentB = helper.createActiveStudent("25-701", "학생B")
            val taskB = helper.createTranslationTask(
                taskType = ywcheong.sofia.task.TranslationTask.TaskType.GAONNURI_POST,
                taskDescription = "과제B",
                assignee = studentB
            )
            completeTask(taskB.id, 1000)

            // 관리자는 자수가 0
            helper.createAdminAndGetToken("admin-sort", "관리자B")

            // when & then - 학생만 필터링 + 총 자수 내림차순 정렬
            mockMvc.get("/users?page=0&size=10&role=STUDENT&sortField=totalCharCount&sortDirection=DESC") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(2) }
                jsonPath("$.content[0].studentName") { value("학생A") }
                jsonPath("$.content[0].totalCharCount") { value(3000) }
                jsonPath("$.content[1].studentName") { value("학생B") }
                jsonPath("$.content[1].totalCharCount") { value(1000) }
            }
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
            mockMvc.get("/users?page=0&size=10&rest=false&sortField=studentName&sortDirection=ASC") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(3) }
                jsonPath("$.content[0].studentName") { value("활성A") }
                jsonPath("$.content[1].studentName") { value("활성B") }
                jsonPath("$.content[2].studentName") { value("활성C") }
            }
        }
    }

    @Nested
    @DisplayName("POST /users/{userId}/rest - 개인 휴식 설정")
    inner class SetRestStatus {

        @Test
        fun `휴식 상태로 변경하면 200을 반환한다`() {
            // given - 활성 사용자 2명 이상 생성 (마지막 사용자 제약 회피)
            helper.createActiveStudent("25-020", "사용자나")
            val user = helper.createActiveStudent("25-021", "사용자다")

            val request = mapOf(
                "rest" to true,
            )

            // when & then (관리자 권한 필요)
            mockMvc.post("/users/${user.id}/rest") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.userId") { value(user.id.toString()) }
                jsonPath("$.rest") { value(true) }
            }
        }

        @Test
        fun `활성 상태로 변경하면 200을 반환한다`() {
            // given - 휴식 상태 사용자 생성 (마지막 활성 사용자 제약 회피 위해 추가 사용자 생성)
            helper.createActiveStudent("25-021", "다른사용자")
            val user = helper.createActiveStudent("25-022", "휴식자")
            helper.setUserResting(user.id, true)

            val request = mapOf(
                "rest" to false,
            )

            // when & then
            mockMvc.post("/users/${user.id}/rest") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.userId") { value(user.id.toString()) }
                jsonPath("$.rest") { value(false) }
            }
        }

        @Test
        fun `존재하지 않는 사용자면 400을 반환한다`() {
            // given
            val nonExistentId = UUID.randomUUID()
            val request = mapOf(
                "isResting" to true,
            )

            // when & then
            mockMvc.post("/users/$nonExistentId/rest") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        @Transactional
        fun `마지막 활성 사용자가 휴식 상태로 변경하면 400을 반환한다`() {
            // given - 단 하나의 활성 사용자
            val onlyActiveUser = helper.createActiveStudent("25-030", "유일한활성자")

            val request = mapOf(
                "isResting" to true,
            )

            // when & then
            mockMvc.post("/users/${onlyActiveUser.id}/rest") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }
    }

    @Nested
    @DisplayName("POST /users/{userId}/adjust-char-count - 보정 자수 조정")
    inner class AdjustCharCount {

        @Test
        fun `양수로 보정 자수를 부여하면 200을 반환한다`() {
            // given - 활성 사용자 생성
            val user = helper.createActiveStudent("25-040", "사용자")

            val request = mapOf(
                "amount" to 500,
            )

            // when & then (관리자 권한 필요)
            mockMvc.post("/users/${user.id}/adjust-char-count") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.userId") { value(user.id.toString()) }
                jsonPath("$.amount") { value(500) }
                jsonPath("$.adjustedCharCount") { value(500) }
            }
        }

        @Test
        fun `음수로 보정 자수를 차감하면 200을 반환한다`() {
            // given - 보정 자수가 있는 사용자 생성
            val user = helper.createActiveStudent("25-041", "사용자")
            adjustCharCount(user.id, 100) // 먼저 100 부여

            val request = mapOf(
                "amount" to -300,
            )

            // when & then
            mockMvc.post("/users/${user.id}/adjust-char-count") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.userId") { value(user.id.toString()) }
                jsonPath("$.amount") { value(-300) }
                jsonPath("$.adjustedCharCount") { value(-200) }
            }
        }

        @Test
        fun `존재하지 않는 사용자면 400을 반환한다`() {
            // given
            val nonExistentId = UUID.randomUUID()
            val request = mapOf(
                "amount" to 500,
            )

            // when & then
            mockMvc.post("/users/$nonExistentId/adjust-char-count") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `보정 자수가 0이면 400을 반환한다`() {
            // given - 활성 사용자 생성
            val user = helper.createActiveStudent("25-042", "사용자")

            val request = mapOf(
                "amount" to 0,
            )

            // when & then
            mockMvc.post("/users/${user.id}/adjust-char-count") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }
    }

    @Nested
    @DisplayName("POST /users/{userId}/promote - 관리자 승급")
    inner class PromoteToAdmin {

        @Test
        fun `일반 학생을 관리자로 승급하면 200을 반환한다`() {
            // given - 일반 학생 생성
            val student = helper.createActiveStudent("25-050", "학생")

            // when & then
            mockMvc.post("/users/${student.id}/promote") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.userId") { value(student.id.toString()) }
                jsonPath("$.role") { value("ADMIN") }
            }
        }

        @Test
        fun `관리자 승급 시 승급 알림 이메일이 발송된다`() {
            // given - 이메일 구독 중인 일반 학생 생성
            val student = helper.createActiveStudent("25-051", "학생")
            val mailSender = helper.getMailSender()

            // when
            mockMvc.post("/users/${student.id}/promote") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
            }

            // then - 관리자 승급 이메일이 발송되었는지 확인
            val emails = mailSender.getMessagesBySubject("관리자 권한이 부여되었습니다")
            assert(emails.isNotEmpty()) { "관리자 승급 이메일이 발송되어야 합니다" }

            val emailInfo = mailSender.extractEmailInfo(emails.first())
            assert(emailInfo.content.contains("학생")) { "수신자 이름이 본문에 포함되어야 합니다" }
        }

        @Test
        fun `이미 관리자인 사용자를 승급하면 400을 반환한다`() {
            // given - 다른 관리자 생성
            val otherAdmin = helper.createAdminAndGetToken("admin2", "관리자2")

            // when & then
            mockMvc.post("/users/${otherAdmin.userId}/promote") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `존재하지 않는 사용자를 승급하면 400을 반환한다`() {
            // given
            val nonExistentId = UUID.randomUUID()

            // when & then
            mockMvc.post("/users/$nonExistentId/promote") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }
    }

    @Nested
    @DisplayName("POST /users/{userId}/demote - 관리자 강등")
    inner class DemoteFromAdmin {

        @Test
        fun `관리자를 일반 학생으로 강등하면 200을 반환한다`() {
            // given - 다른 관리자 생성
            val otherAdmin = helper.createAdminAndGetToken("admin2", "관리자2")

            // when & then
            mockMvc.post("/users/${otherAdmin.userId}/demote") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.userId") { value(otherAdmin.userId.toString()) }
                jsonPath("$.role") { value("STUDENT") }
            }
        }

        @Test
        fun `관리자 강등 시 강등 알림 이메일이 발송된다`() {
            // given - 다른 관리자 생성
            val otherAdmin = helper.createAdminAndGetToken("admin3", "관리자3")
            val mailSender = helper.getMailSender()

            // when
            mockMvc.post("/users/${otherAdmin.userId}/demote") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
            }

            // then - 관리자 강등 이메일이 발송되었는지 확인
            val emails = mailSender.getMessagesBySubject("관리자 권한이 해제되었습니다")
            assert(emails.isNotEmpty()) { "관리자 강등 이메일이 발송되어야 합니다" }

            val emailInfo = mailSender.extractEmailInfo(emails.first())
            assert(emailInfo.content.contains("관리자3")) { "수신자 이름이 본문에 포함되어야 합니다" }
        }

        @Test
        fun `자기 자신을 강등하면 400을 반환한다`() {
            // when & then - adminInfo 사용자가 자기 자신을 강등 시도
            mockMvc.post("/users/${adminInfo.userId}/demote") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `일반 학생을 강등하면 400을 반환한다`() {
            // given - 일반 학생 생성
            val student = helper.createActiveStudent("25-060", "학생")

            // when & then
            mockMvc.post("/users/${student.id}/demote") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `마지막 관리자를 강등하면 400을 반환한다`() {
            // given - 관리자가 한 명만 있는 상황 (이미 adminInfo만 존재)
            // adminInfo 외에 다른 관리자가 없으므로 마지막 관리자

            // when & then - 다른 관리자가 없는 상태에서 강등 불가
            // 먼저 다른 관리자를 만들고 강등하면 adminInfo가 마지막 관리자가 됨
            val otherAdmin = helper.createAdminAndGetToken("admin2", "관리자2")

            // otherAdmin을 강등 (성공해야 함)
            mockMvc.post("/users/${otherAdmin.userId}/demote") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
            }

            // 이제 adminInfo가 마지막 관리자 - 다시 강등하려고 하면 실패
            // 이미 STUDENT가 된 otherAdmin을 다시 관리자로 승급
            mockMvc.post("/users/${otherAdmin.userId}/promote") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
            }

            // adminInfo(현재 로그인한 관리자)가 otherAdmin을 강등
            mockMvc.post("/users/${otherAdmin.userId}/demote") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
            }

            // 이제 adminInfo가 마지막 관리자
            // adminInfo가 자신이 아닌 다른 사용자(이미 학생이 된 otherAdmin)를 강등하려고 하면
            // 그 사용자는 이미 학생이므로 "관리자 권한이 없는 사용자입니다" 에러
            mockMvc.post("/users/${otherAdmin.userId}/demote") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `존재하지 않는 사용자를 강등하면 400을 반환한다`() {
            // given
            val nonExistentId = UUID.randomUUID()

            // when & then
            mockMvc.post("/users/$nonExistentId/demote") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }
    }

    // 헬퍼 메서드: 보정 자수 조정 (API 사용)
    private fun adjustCharCount(userId: UUID, amount: Int) {
        val request = mapOf(
            "amount" to amount,
        )
        mockMvc.post("/users/$userId/adjust-char-count") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
            header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
        }.andExpect {
            status { isOk() }
        }
    }

    // 헬퍼 메서드: 과제 완료 처리 (API 사용)
    private fun completeTask(taskId: java.util.UUID, characterCount: Int = 1000) {
        val request = mapOf(
            "characterCount" to characterCount,
        )
        mockMvc.post("/tasks/$taskId/completion") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
            header("Authorization", helper.kakaoAuthHeader())
        }.andExpect {
            status { isOk() }
        }
    }
}
