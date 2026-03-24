package ywcheong.sofia.user.boot

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import ywcheong.sofia.user.SofiaUser
import ywcheong.sofia.user.SofiaUserRepository
import ywcheong.sofia.user.SofiaUserRole
import ywcheong.sofia.user.auth.SofiaUserAuthRepository

@Component
class SofiaFirstBootAdminInitializer(
    private val userRepository: SofiaUserRepository,
    private val userAuthRepository: SofiaUserAuthRepository,
    private val properties: SofiaFirstBootProperties,
) : CommandLineRunner {

    private val logger = KotlinLogging.logger {}

    override fun run(vararg args: String) {
        if (!properties.createAdminIfEmpty) {
            if (userAuthRepository.countByRole(SofiaUserRole.ADMIN) > 0) {
                logger.warn { "데이터베이스에 관리자가 존재하기 때문에 관리자를 생성하지 않았습니다. 이 기능을 비활성화하기 위해 `sofia.first-boot`를 수정하세요." }
                return
            }
        }

        val adminUser = SofiaUser(
            studentNumber = properties.adminStudentNumber,
            studentName = "Administrator",
            plusfriendUserKey = "admin-${properties.adminStudentNumber}",
            role = SofiaUserRole.ADMIN,
        )
        userRepository.save(adminUser)

        logger.warn {
            """
            |[최초 부팅] 관리자 계정이 생성되었습니다.
            |   - 학번: ${adminUser.studentNumber}
            |   - 인증 토큰: ${adminUser.auth.secretToken}
            |   ※ 로그인 후 즉시 인증 토큰을 변경하세요.
            """.trimMargin()
        }
    }
}
