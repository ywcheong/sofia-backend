package ywcheong.sofia.task.user

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ywcheong.sofia.commons.BusinessException
import ywcheong.sofia.user.SofiaUser
import java.time.Instant

@Service
class SofiaUserRoundRobinService(
    private val sofiaUserTaskStatusRepository: SofiaUserTaskStatusRepository,
) {
    @Transactional
    fun getNextAssignee(): SofiaUser {
        val userTask = sofiaUserTaskStatusRepository.findNextAssignee()
            ?: throw BusinessException("할당 가능한 번역버디가 없습니다.")

        userTask.lastAssignedAt = Instant.now()
        return userTask.user
    }
}
