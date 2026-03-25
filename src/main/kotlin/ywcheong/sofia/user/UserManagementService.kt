package ywcheong.sofia.user

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.EntityManager
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ywcheong.sofia.commons.BusinessException
import ywcheong.sofia.commons.SortDirection
import ywcheong.sofia.commons.SortRequest
import ywcheong.sofia.email.EmailSendService
import ywcheong.sofia.email.templates.DemotedFromAdminEmailTemplate
import ywcheong.sofia.email.templates.PromotedToAdminEmailTemplate
import ywcheong.sofia.task.TranslationTaskRepository
import ywcheong.sofia.task.user.SofiaUserTaskStatus
import ywcheong.sofia.task.user.SofiaUserTaskStatusRepository
import ywcheong.sofia.user.auth.SofiaUserAuth
import ywcheong.sofia.user.auth.SofiaUserAuthRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

@Service
class UserManagementService(
    private val sofiaUserRepository: SofiaUserRepository,
    private val sofiaUserTaskStatusRepository: SofiaUserTaskStatusRepository,
    private val sofiaUserAuthRepository: SofiaUserAuthRepository,
    private val translationTaskRepository: TranslationTaskRepository,
    private val emailSendService: EmailSendService,
    private val entityManager: EntityManager,
) {
    private val logger = KotlinLogging.logger {}

    companion object {
        val ALLOWED_SORT_FIELDS = setOf(
            "id", "studentNumber", "studentName", "role", "rest", "warningCount", "totalCharCount"
        )
    }

    /**
     * 사용자 목록 조회 결과
     */
    data class FindAllUsersResult(
        val id: UUID,
        val studentNumber: String,
        val studentName: String,
        val role: SofiaUserRole,
        val rest: Boolean,
        val warningCount: Int,
        val adjustedCharCount: Int,
        val completedCharCount: Int,
        val totalCharCount: Int,
    )

    /**
     * 사용자 목록 조회 조건
     */
    data class FindAllUsersCondition(
        val search: String?,
        val role: SofiaUserRole?,
        val rest: Boolean?,
    )

    fun findAllUsers(
        condition: FindAllUsersCondition,
        sortRequest: SortRequest,
        pageable: Pageable
    ): Page<FindAllUsersResult> {
        sortRequest.validate()

        // 정렬 필드 검증
        val sortField = sortRequest.sortField
        if (sortField != null && sortField !in ALLOWED_SORT_FIELDS) {
            throw BusinessException("정렬할 수 없는 필드입니다: $sortField. 허용된 필드: ${ALLOWED_SORT_FIELDS.joinToString()}")
        }

        // totalCharCount 정렬 시 특별 처리 (DB 레벨 서브쿼리 사용)
        if (sortField == "totalCharCount") {
            return findAllUsersWithTotalCharCountSort(condition, sortRequest.sortDirection!!, pageable)
        }

        // 기본 정렬 처리 - Criteria API 사용
        return findAllUsersWithBasicSort(condition, sortField, sortRequest.sortDirection, pageable)
    }

    /**
     * 기본 필드 정렬 처리 (totalCharCount 제외)
     */
    private fun findAllUsersWithBasicSort(
        condition: FindAllUsersCondition,
        sortField: String?,
        sortDirection: SortDirection?,
        pageable: Pageable
    ): Page<FindAllUsersResult> {
        val cb = entityManager.criteriaBuilder
        val cq = cb.createQuery(SofiaUser::class.java)
        val root = cq.from(SofiaUser::class.java)

        // 조건 필터 적용
        val predicates = createFilterPredicates(condition, root, cb)
        cq.where(*predicates.toTypedArray())

        // 정렬 적용
        if (sortField != null) {
            val jpaDirection = sortDirection?.toSortDirection() ?: Sort.Direction.ASC
            val sortProperty = mapSortFieldToProperty(sortField)

            // 연관관계가 있는 필드 처리
            val orderExpression = when (sortProperty) {
                "auth.role" -> {
                    val authJoin = root.join<SofiaUser, SofiaUserAuth>("auth")
                    authJoin.get<SofiaUserRole>("role")
                }
                "taskStatus.rest" -> {
                    val taskStatusJoin = root.join<SofiaUser, SofiaUserTaskStatus>("taskStatus")
                    taskStatusJoin.get<Boolean>("rest")
                }
                "taskStatus.warningCount" -> {
                    val taskStatusJoin = root.join<SofiaUser, SofiaUserTaskStatus>("taskStatus")
                    taskStatusJoin.get<Int>("warningCount")
                }
                else -> root.get<Any>(sortProperty)
            }

            cq.orderBy(
                if (jpaDirection == Sort.Direction.ASC) cb.asc(orderExpression)
                else cb.desc(orderExpression)
            )
        }

        // 전체 카운트 쿼리
        val countQuery = cb.createQuery(Long::class.java)
        val countRoot = countQuery.from(SofiaUser::class.java)
        val countPredicates = createFilterPredicates(condition, countRoot, cb)
        countQuery.where(*countPredicates.toTypedArray())
        countQuery.select(cb.count(countRoot))
        val totalCount = entityManager.createQuery(countQuery).singleResult

        // 페이징 적용하여 조회
        val query = entityManager.createQuery(cq)
        query.firstResult = pageable.pageNumber * pageable.pageSize
        query.maxResults = pageable.pageSize
        val users = query.resultList

        // 결과 변환
        val userIds = users.map { it.id }
        val charCountMap = translationTaskRepository
            .sumCharacterCountByAssigneeIn(userIds)
            .associate { row ->
                val userId = row[0] as UUID
                val charCount = (row[1] as Number).toInt()
                userId to charCount
            }

        val results = users.map { user ->
            val completedCharCount = charCountMap[user.id] ?: 0
            val adjustedCharCount = user.taskStatus.adjustedCharCount
            val totalCharCount = completedCharCount + adjustedCharCount
            FindAllUsersResult(
                id = user.id,
                studentNumber = user.studentNumber,
                studentName = user.studentName,
                role = user.auth.role,
                rest = user.taskStatus.rest,
                warningCount = user.taskStatus.warningCount,
                adjustedCharCount = adjustedCharCount,
                completedCharCount = completedCharCount,
                totalCharCount = totalCharCount,
            )
        }

        return PageImpl(results, pageable, totalCount)
    }

    /**
     * totalCharCount 정렬 시 인메모리 정렬 사용
     * (계산된 필드이므로 DB 레벨 정렬이 복잡하여 메모리에서 처리)
     */
    private fun findAllUsersWithTotalCharCountSort(
        condition: FindAllUsersCondition,
        sortDirection: SortDirection,
        pageable: Pageable
    ): Page<FindAllUsersResult> {
        val cb = entityManager.criteriaBuilder
        val cq = cb.createQuery(SofiaUser::class.java)
        val root = cq.from(SofiaUser::class.java)

        // 조건 필터 적용 (정렬 없이 모든 데이터 조회)
        val predicates = createFilterPredicates(condition, root, cb)
        cq.where(*predicates.toTypedArray())

        val users = entityManager.createQuery(cq).resultList

        // 결과 변환 및 totalCharCount 계산
        val userIds = users.map { it.id }
        val charCountMap = translationTaskRepository
            .sumCharacterCountByAssigneeIn(userIds)
            .associate { row ->
                val userId = row[0] as UUID
                val charCount = (row[1] as Number).toInt()
                userId to charCount
            }

        val results = users.map { user ->
            val completedCharCount = charCountMap[user.id] ?: 0
            val adjustedCharCount = user.taskStatus.adjustedCharCount
            val totalCharCount = completedCharCount + adjustedCharCount
            FindAllUsersResult(
                id = user.id,
                studentNumber = user.studentNumber,
                studentName = user.studentName,
                role = user.auth.role,
                rest = user.taskStatus.rest,
                warningCount = user.taskStatus.warningCount,
                adjustedCharCount = adjustedCharCount,
                completedCharCount = completedCharCount,
                totalCharCount = totalCharCount,
            )
        }

        // 인메모리 정렬
        val sortedResults = if (sortDirection == SortDirection.ASC) {
            results.sortedBy { it.totalCharCount }
        } else {
            results.sortedByDescending { it.totalCharCount }
        }

        // 페이징 적용
        val totalCount = sortedResults.size.toLong()
        val startIndex = pageable.pageNumber * pageable.pageSize
        val endIndex = minOf(startIndex + pageable.pageSize, sortedResults.size)
        val pagedResults = if (startIndex < sortedResults.size) {
            sortedResults.subList(startIndex, endIndex)
        } else {
            emptyList()
        }

        return PageImpl(pagedResults, pageable, totalCount)
    }

    /**
     * 필터 조건에 대한 Predicate 목록 생성
     */
    private fun createFilterPredicates(
        condition: FindAllUsersCondition,
        root: Root<SofiaUser>,
        cb: CriteriaBuilder
    ): List<Predicate> {
        val predicates = mutableListOf<Predicate>()

        // 검색어 필터 (학번 또는 이름에 포함, 대소문자 무시)
        condition.search?.let { search ->
            val searchPattern = "%${search.lowercase()}%"
            val studentNumberLike = cb.like(cb.lower(root.get("studentNumber")), searchPattern)
            val studentNameLike = cb.like(cb.lower(root.get("studentName")), searchPattern)
            predicates.add(cb.or(studentNumberLike, studentNameLike))
        }

        // 권한 필터
        condition.role?.let { role ->
            val authJoin = root.join<SofiaUser, SofiaUserAuth>("auth")
            predicates.add(cb.equal(authJoin.get<SofiaUserRole>("role"), role))
        }

        // 휴식 상태 필터
        condition.rest?.let { rest ->
            val taskStatusJoin = root.join<SofiaUser, SofiaUserTaskStatus>("taskStatus")
            predicates.add(cb.equal(taskStatusJoin.get<Boolean>("rest"), rest))
        }

        return predicates
    }

    /**
     * API 필드명을 엔티티 속성명으로 매핑
     */
    private fun mapSortFieldToProperty(sortField: String): String {
        return when (sortField) {
            "id" -> "id"
            "studentNumber" -> "studentNumber"
            "studentName" -> "studentName"
            "role" -> "auth.role"
            "rest" -> "taskStatus.rest"
            "warningCount" -> "taskStatus.warningCount"
            else -> sortField
        }
    }

    data class SetRestStatusCommand(
        val userId: UUID,
        val rest: Boolean,
    )

    data class AdjustCharCountCommand(
        val userId: UUID,
        val amount: Int,
    ) {
        init {
            if (amount == 0) {
                throw BusinessException("보정 자수는 0이 될 수 없습니다.")
            }
        }
    }

    @Transactional
    fun setRestStatus(command: SetRestStatusCommand): SofiaUser {
        val userTask = sofiaUserTaskStatusRepository.findByIdOrNull(command.userId)
            ?: throw BusinessException("존재하지 않는 사용자입니다.")

        // 이미 동일한 상태인 경우
        if (userTask.rest == command.rest) {
            return userTask.user
        }

        // 휴식 상태로 변경 시, 마지막 활성 사용자인지 확인
        if (command.rest) {
            val activeCount = sofiaUserTaskStatusRepository.countByRest(false)
            if (activeCount <= 1) {
                throw BusinessException("모든 번역버디가 휴식 상태가 되어 과제를 할당할 수 없습니다.")
            }
        }

        userTask.updateRestStatus(command.rest)
        logger.info { "사용자 휴식 상태 변경: userId=${command.userId}, isResting=${command.rest}" }

        return userTask.user
    }

    @Transactional
    fun adjustCharCount(command: AdjustCharCountCommand): SofiaUser {
        val userTask = sofiaUserTaskStatusRepository.findByIdOrNull(command.userId)
            ?: throw BusinessException("존재하지 않는 사용자입니다.")

        userTask.adjustCharCount(command.amount)

        val action = if (command.amount > 0) "부여" else "차감"
        logger.info { "보정 자수 $action: userId=${command.userId}, amount=${command.amount}, newAdjustedCharCount=${userTask.adjustedCharCount}" }

        return userTask.user
    }

    // UC-014: 관리자 승급
    data class PromoteToAdminCommand(
        val userId: UUID,
    )

    // UC-015: 관리자 강등
    data class DemoteFromAdminCommand(
        val userId: UUID,
        val currentUserId: UUID,
    )

    @Transactional
    fun promoteToAdmin(command: PromoteToAdminCommand): SofiaUser {
        val userAuth = sofiaUserAuthRepository.findByIdOrNull(command.userId)
            ?: throw BusinessException("존재하지 않는 사용자입니다.")

        if (userAuth.role == SofiaUserRole.ADMIN) {
            throw BusinessException("이미 관리자 권한을 가진 사용자입니다.")
        }

        userAuth.updateRole(SofiaUserRole.ADMIN)
        logger.info { "관리자 승급: userId=${command.userId}" }

        // US-029: 관리자 승급 이메일 발송
        sendPromotedToAdminEmail(userAuth.user)

        return userAuth.user
    }

    @Transactional
    fun demoteFromAdmin(command: DemoteFromAdminCommand): SofiaUser {
        // 자기 자신을 강등할 수 없음
        if (command.userId == command.currentUserId) {
            throw BusinessException("자기 자신을 강등할 수 없습니다.")
        }

        val userAuth = sofiaUserAuthRepository.findByIdOrNull(command.userId)
            ?: throw BusinessException("존재하지 않는 사용자입니다.")

        if (userAuth.role == SofiaUserRole.STUDENT) {
            throw BusinessException("관리자 권한이 없는 사용자입니다.")
        }

        // 마지막 관리자인지 확인
        val adminCount = sofiaUserAuthRepository.countByRole(SofiaUserRole.ADMIN)
        if (adminCount <= 1) {
            throw BusinessException("마지막 관리자는 강등할 수 없습니다.")
        }

        userAuth.updateRole(SofiaUserRole.STUDENT)
        logger.info { "관리자 강등: userId=${command.userId}" }

        // US-029: 관리자 강등 이메일 발송
        sendDemotedFromAdminEmail(userAuth.user)

        return userAuth.user
    }

    private fun sendPromotedToAdminEmail(user: SofiaUser) {
        val template = PromotedToAdminEmailTemplate(
            recipientEmail = user.email.email,
            recipientName = user.studentName,
            unsubscribeToken = user.email.unsubscribeToken.toString(),
            promotedAt = formatDateTime(Instant.now()),
        )
        emailSendService.sendEmail(template)
    }

    private fun sendDemotedFromAdminEmail(user: SofiaUser) {
        val template = DemotedFromAdminEmailTemplate(
            recipientEmail = user.email.email,
            recipientName = user.studentName,
            unsubscribeToken = user.email.unsubscribeToken.toString(),
            demotionReason = "관리자 권한이 해제되었습니다.",
            demotedAt = formatDateTime(Instant.now()),
        )
        emailSendService.sendEmail(template)
    }

    private fun formatDateTime(instant: Instant): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.of("Asia/Seoul"))
        return formatter.format(instant)
    }
}
