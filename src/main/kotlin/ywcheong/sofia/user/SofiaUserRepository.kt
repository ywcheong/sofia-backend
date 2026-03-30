package ywcheong.sofia.user

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface SofiaUserRepository : JpaRepository<SofiaUser, UUID>, JpaSpecificationExecutor<SofiaUser> {
    fun existsByStudentNumber(studentNumber: String): Boolean
    fun findByAuthPlusfriendUserKey(plusfriendUserKey: String): SofiaUser?

    fun findByAuthRole(role: SofiaUserRole): List<SofiaUser>

    fun deleteByAuthRole(role: SofiaUserRole)

    @Query(
        value = """
        SELECT
            u.id as id,
            u.student_number as studentNumber,
            u.student_name as studentName,
            a.role as role,
            ts.rest as rest,
            ts.warning_count as warningCount,
            ts.adjusted_char_count as adjustedCharCount,
            COALESCE(t.completed_char_count, 0) as completedCharCount,
            ts.adjusted_char_count + COALESCE(t.completed_char_count, 0) as totalCharCount
        FROM sofia_user u
        JOIN sofia_user_auth a ON u.id = a.id
        JOIN sofia_user_task_status ts ON u.id = ts.id
        LEFT JOIN (
            SELECT assignee_id, SUM(character_count) as completed_char_count
            FROM translation_task
            WHERE completed_at IS NOT NULL
            GROUP BY assignee_id
        ) t ON u.id = t.assignee_id
        WHERE (:search IS NULL
               OR LOWER(u.student_number) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(u.student_name) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:role IS NULL OR a.role = :role)
          AND (:rest IS NULL OR ts.rest = :rest)
        ORDER BY
            CASE WHEN :sortField = 'studentNumber' AND :sortDir = 'ASC' THEN u.student_number END ASC,
            CASE WHEN :sortField = 'studentNumber' AND :sortDir = 'DESC' THEN u.student_number END DESC,
            CASE WHEN :sortField = 'studentName' AND :sortDir = 'ASC' THEN u.student_name END ASC,
            CASE WHEN :sortField = 'studentName' AND :sortDir = 'DESC' THEN u.student_name END DESC,
            CASE WHEN :sortField = 'role' AND :sortDir = 'ASC' THEN a.role END ASC,
            CASE WHEN :sortField = 'role' AND :sortDir = 'DESC' THEN a.role END DESC,
            CASE WHEN :sortField = 'rest' AND :sortDir = 'ASC' THEN ts.rest END ASC,
            CASE WHEN :sortField = 'rest' AND :sortDir = 'DESC' THEN ts.rest END DESC,
            CASE WHEN :sortField = 'warningCount' AND :sortDir = 'ASC' THEN ts.warning_count END ASC,
            CASE WHEN :sortField = 'warningCount' AND :sortDir = 'DESC' THEN ts.warning_count END DESC,
            CASE WHEN :sortField = 'totalCharCount' AND :sortDir = 'ASC'
                 THEN ts.adjusted_char_count + COALESCE(t.completed_char_count, 0) END ASC,
            CASE WHEN :sortField = 'totalCharCount' AND :sortDir = 'DESC'
                 THEN ts.adjusted_char_count + COALESCE(t.completed_char_count, 0) END DESC,
            u.id ASC
        """,
        countQuery = """
        SELECT COUNT(u.id)
        FROM sofia_user u
        JOIN sofia_user_auth a ON u.id = a.id
        JOIN sofia_user_task_status ts ON u.id = ts.id
        WHERE (:search IS NULL
               OR LOWER(u.student_number) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(u.student_name) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:role IS NULL OR a.role = :role)
          AND (:rest IS NULL OR ts.rest = :rest)
        """,
        nativeQuery = true
    )
    fun findAllUsersForManagement(
        @Param("search") search: String?,
        @Param("role") role: String?,
        @Param("rest") rest: Boolean?,
        @Param("sortField") sortField: String?,
        @Param("sortDir") sortDir: String?,
        pageable: Pageable
    ): Page<UserManagementProjection>
}

interface UserManagementProjection {
    fun getId(): UUID
    fun getStudentNumber(): String
    fun getStudentName(): String
    fun getRole(): String
    fun getRest(): Boolean
    fun getWarningCount(): Int
    fun getAdjustedCharCount(): Int
    fun getCompletedCharCount(): Int
    fun getTotalCharCount(): Int
}