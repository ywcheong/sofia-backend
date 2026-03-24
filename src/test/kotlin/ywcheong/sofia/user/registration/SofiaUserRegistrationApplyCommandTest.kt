package ywcheong.sofia.user.registration

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import ywcheong.sofia.commons.BusinessException

@DisplayName("회원가입 신청 Command 검증")
class SofiaUserRegistrationApplyCommandTest {

    @Nested
    @DisplayName("학번 형식 검증")
    inner class StudentNumberValidation {

        @Test
        fun `유효한 학번은 예외를 던지지 않는다`() {
            assertDoesNotThrow {
                UserRegistrationService.ApplyCommand(
                    studentNumber = "25-001",
                    studentName = "홍길동",
                    plusfriendUserKey = "test-user-key"
                )
            }
        }

        @Test
        fun `학번이 형식에 맞지 않으면 BusinessException을 던진다`() {
            assertThrows<BusinessException> {
                UserRegistrationService.ApplyCommand(
                    studentNumber = "invalid",
                    studentName = "홍길동",
                    plusfriendUserKey = "test-user-key"
                )
            }
        }

        @Test
        fun `학번이 너무 짧으면 BusinessException을 던진다`() {
            assertThrows<BusinessException> {
                UserRegistrationService.ApplyCommand(
                    studentNumber = "25-00",
                    studentName = "홍길동",
                    plusfriendUserKey = "test-user-key"
                )
            }
        }

        @Test
        fun `학번이 너무 길면 BusinessException을 던진다`() {
            assertThrows<BusinessException> {
                UserRegistrationService.ApplyCommand(
                    studentNumber = "25-0001",
                    studentName = "홍길동",
                    plusfriendUserKey = "test-user-key"
                )
            }
        }

        @Test
        fun `학번에 하이픈이 없으면 BusinessException을 던진다`() {
            assertThrows<BusinessException> {
                UserRegistrationService.ApplyCommand(
                    studentNumber = "25001",
                    studentName = "홍길동",
                    plusfriendUserKey = "test-user-key"
                )
            }
        }
    }

    @Nested
    @DisplayName("이름 형식 검증")
    inner class StudentNameValidation {

        @Test
        fun `유효한 한글 이름은 예외를 던지지 않는다`() {
            assertDoesNotThrow {
                UserRegistrationService.ApplyCommand(
                    studentNumber = "25-001",
                    studentName = "홍길동",
                    plusfriendUserKey = "test-user-key"
                )
            }
        }

        @Test
        fun `유효한 영어 이름은 예외를 던지지 않는다`() {
            assertDoesNotThrow {
                UserRegistrationService.ApplyCommand(
                    studentNumber = "25-001",
                    studentName = "John Doe",
                    plusfriendUserKey = "test-user-key"
                )
            }
        }

        @Test
        fun `이름이 한 글자이면 BusinessException을 던진다`() {
            assertThrows<BusinessException> {
                UserRegistrationService.ApplyCommand(
                    studentNumber = "25-001",
                    studentName = "1",
                    plusfriendUserKey = "test-user-key"
                )
            }
        }

        @Test
        fun `이름에 숫자가 포함되면 BusinessException을 던진다`() {
            assertThrows<BusinessException> {
                UserRegistrationService.ApplyCommand(
                    studentNumber = "25-001",
                    studentName = "홍길동1",
                    plusfriendUserKey = "test-user-key"
                )
            }
        }

        @Test
        fun `이름에 특수문자가 포함되면 BusinessException을 던진다`() {
            assertThrows<BusinessException> {
                UserRegistrationService.ApplyCommand(
                    studentNumber = "25-001",
                    studentName = "홍길동!",
                    plusfriendUserKey = "test-user-key"
                )
            }
        }

        @Test
        fun `이름이 30자를 초과하면 BusinessException을 던진다`() {
            assertThrows<BusinessException> {
                UserRegistrationService.ApplyCommand(
                    studentNumber = "25-001",
                    studentName = "가".repeat(31),
                    plusfriendUserKey = "test-user-key"
                )
            }
        }
    }
}