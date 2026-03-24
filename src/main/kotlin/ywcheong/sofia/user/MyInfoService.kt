package ywcheong.sofia.user

import org.springframework.stereotype.Service
import ywcheong.sofia.task.TranslationTaskProperties
import ywcheong.sofia.task.TranslationTaskRepository
import ywcheong.sofia.user.registration.UserRegistration
import ywcheong.sofia.user.registration.UserRegistrationRepository

@Service
class MyInfoService(
    private val taskRepository: TranslationTaskRepository,
    private val taskProperties: TranslationTaskProperties,
    private val registrationRepository: UserRegistrationRepository,
) {
    fun getMyInfo(user: SofiaUser): MyInfoResult {
        val completedCharCount = taskRepository.sumCharacterCountByAssignee(user)
        val adjustedCharCount = user.taskStatus.adjustedCharCount
        val totalCharCount = completedCharCount + adjustedCharCount
        val estimatedSeconds = (totalCharCount * taskProperties.secondsPerCharacter).toInt()
        val warningCount = user.taskStatus.warningCount

        return MyInfoResult(
            roleText = user.auth.role.displayText,
            studentNumber = user.studentNumber,
            studentName = user.studentName,
            completedCharCount = completedCharCount,
            adjustedCharCount = adjustedCharCount,
            estimatedSeconds = estimatedSeconds,
            warningCount = warningCount,
        )
    }

    fun findPendingRegistration(plusfriendUserKey: String): UserRegistration? {
        return registrationRepository.findByPlusfriendUserKey(plusfriendUserKey)
    }
}

data class MyInfoResult(
    val roleText: String,
    val studentNumber: String,
    val studentName: String,
    val completedCharCount: Int,
    val adjustedCharCount: Int,
    val estimatedSeconds: Int,
    val warningCount: Int,
) {
    fun formatEstimatedDuration(): String {
        val hours = estimatedSeconds / 3600
        val minutes = (estimatedSeconds % 3600) / 60
        val secs = estimatedSeconds % 60

        return buildString {
            if (hours > 0) append("${hours}시간 ")
            if (minutes > 0) append("${minutes}분 ")
            if (secs > 0 || isEmpty()) append("${secs}초")
        }.trim()
    }
}
