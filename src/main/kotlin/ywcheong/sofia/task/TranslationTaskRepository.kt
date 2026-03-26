package ywcheong.sofia.task

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface TranslationTaskRepository : JpaRepository<TranslationTask, UUID>, JpaSpecificationExecutor<TranslationTask> {
    fun existsByTaskTypeAndTaskDescription(taskType: TranslationTask.TaskType, taskDescription: String): Boolean

    fun findByAssigneeAndCompletedAtIsNotNull(assignee: ywcheong.sofia.user.SofiaUser): List<TranslationTask>

    @Query("SELECT t FROM TranslationTask t WHERE t.completedAt IS NULL")
    fun findAllIncompleteTasks(): List<TranslationTask>

    @Query("SELECT COALESCE(SUM(t.characterCount), 0) FROM TranslationTask t WHERE t.completedAt IS NOT NULL AND t.assignee = :assignee")
    fun sumCharacterCountByAssignee(assignee: ywcheong.sofia.user.SofiaUser): Int

        /**
     * 미완료 && 리마인드 안 됨 && 할당 후 threshold 이상 경과한 과제 조회
     * 48시간 리마인드 대상 찾기용
     */
    @Query("SELECT t FROM TranslationTask t WHERE t.completedAt IS NULL AND t.remindedAt IS NULL AND t.assignedAt <= :threshold")
    fun findTasksNeedingReminder(threshold: Instant): List<TranslationTask>

    /**
     * 특정 사용자의 미완료 과제를 배정된 지 오래된 순으로 조회
     */
    fun findByAssigneeAndCompletedAtIsNullOrderByAssignedAtAsc(assignee: ywcheong.sofia.user.SofiaUser): List<TranslationTask>
}
