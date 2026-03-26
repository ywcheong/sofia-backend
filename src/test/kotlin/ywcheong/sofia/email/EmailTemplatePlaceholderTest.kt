package ywcheong.sofia.email

import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ResourceLoader
import ywcheong.sofia.email.templates.*
import java.util.regex.Pattern
import kotlin.test.assertEquals

private val logger = KotlinLogging.logger {}

@SpringBootTest
@DisplayName("이메일 템플릿")
class EmailTemplatePlaceholderTest {

    @Autowired
    private lateinit var resourceLoader: ResourceLoader

    companion object {
        private const val TEMPLATE_BASE_PATH = "classpath:/static/email-templates/"
        private const val TEMPLATE_EXTENSION = ".html"
        private const val HEADER_TEMPLATE_ID = "_header"
        private const val FOOTER_TEMPLATE_ID = "_footer"
        private val PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}")

        @JvmStatic
        fun allEmailTemplates(): List<EmailTemplate> = listOf(
            TaskAssignmentEmailTemplate(
                recipientEmail = "test@example.com",
                recipientName = "테스트",
                unsubscribeToken = "token",
                taskTitle = "dummy",
                taskDeadline = "dummy",
                taskLink = "dummy"
            ),
            WarningIssuedEmailTemplate(
                recipientEmail = "test@example.com",
                recipientName = "테스트",
                unsubscribeToken = "token",
                warningReason = "dummy",
                warningCount = 1,
                issuedAt = "dummy"
            ),
            RegistrationApprovedEmailTemplate(
                recipientEmail = "test@example.com",
                recipientName = "테스트",
                unsubscribeToken = "token",
                approvedAt = "dummy"
            ),
            RegistrationRejectedEmailTemplate(
                recipientEmail = "test@example.com",
                recipientName = "테스트",
                unsubscribeToken = "token",
                rejectionReason = "dummy",
                rejectedAt = "dummy"
            ),
            PromotedToAdminEmailTemplate(
                recipientEmail = "test@example.com",
                recipientName = "테스트",
                unsubscribeToken = "token",
                promotedAt = "dummy"
            ),
            DemotedFromAdminEmailTemplate(
                recipientEmail = "test@example.com",
                recipientName = "테스트",
                unsubscribeToken = "token",
                demotionReason = "dummy",
                demotedAt = "dummy"
            ),
            TaskReminderEmailTemplate(
                recipientEmail = "test@example.com",
                recipientName = "테스트",
                unsubscribeToken = "token",
                taskTitle = "dummy",
                assignedAt = "dummy",
                taskLink = "dummy"
            ),
            TaskReminderAdminNotificationEmailTemplate(
                recipientEmail = "test@example.com",
                recipientName = "테스트",
                unsubscribeToken = "token",
                buddyName = "dummy",
                taskTitle = "dummy",
                assignedAt = "dummy"
            ),
        )
    }

    @ParameterizedTest
    @MethodSource("allEmailTemplates")
    @DisplayName("placeholder 제공 검증")
    fun `모든 EmailTemplate 구현체는 대응하는 HTML 템플릿의 placeholder를 올바르게 제공해야 함`(template: EmailTemplate) {
        // when: header + body + footer 템플릿에서 placeholder 추출 (header와 footer 포함)
        val htmlPlaceholders = mutableSetOf<String>()
        htmlPlaceholders.addAll(extractPlaceholdersFromTemplate(HEADER_TEMPLATE_ID))
        htmlPlaceholders.addAll(extractPlaceholdersFromTemplate(template.templateId))
        htmlPlaceholders.addAll(extractPlaceholdersFromTemplate(FOOTER_TEMPLATE_ID))

        // when: EmailTemplate의 toPlaceholderMap에서 key 추출
        val templatePlaceholders = template.toPlaceholderMap().keys

        // then: 두 keyset이 일치해야 함
        val missingInTemplate = htmlPlaceholders - templatePlaceholders
        val extraInTemplate = templatePlaceholders - htmlPlaceholders

        assertEquals(
            expected = htmlPlaceholders,
            actual = templatePlaceholders,
            message = buildErrorMessage(
                template::class.simpleName ?: "Unknown",
                htmlPlaceholders,
                templatePlaceholders,
                missingInTemplate,
                extraInTemplate
            )
        )

        logger.info { "✓ ${template::class.simpleName}: placeholder 일치 (${htmlPlaceholders.size}개)" }
    }

    private fun extractPlaceholdersFromTemplate(templateId: String): Set<String> {
        val resourcePath = "$TEMPLATE_BASE_PATH$templateId$TEMPLATE_EXTENSION"
        val resource = resourceLoader.getResource(resourcePath)

        check(resource.exists()) { "HTML 템플릿 파일을 찾을 수 없습니다: $resourcePath" }

        val htmlContent = resource.inputStream.use { inputStream ->
            String(inputStream.readAllBytes(), Charsets.UTF_8)
        }
        val placeholders = mutableSetOf<String>()
        val matcher = PLACEHOLDER_PATTERN.matcher(htmlContent)
        while (matcher.find()) {
            placeholders.add(matcher.group(1))
        }
        return placeholders
    }

    private fun buildErrorMessage(
        className: String,
        htmlPlaceholders: Set<String>,
        templatePlaceholders: Set<String>,
        missingInTemplate: Set<String>,
        extraInTemplate: Set<String>
    ): String {
        return buildString {
            appendLine("$className: placeholder 불일치")
            appendLine("  HTML placeholders: $htmlPlaceholders")
            appendLine("  Template placeholders: $templatePlaceholders")
            if (missingInTemplate.isNotEmpty()) {
                appendLine("  ❌ Template에 누락된 key: $missingInTemplate")
            }
            if (extraInTemplate.isNotEmpty()) {
                appendLine("  ❌ Template에 불필요한 key: $extraInTemplate")
            }
        }
    }
}