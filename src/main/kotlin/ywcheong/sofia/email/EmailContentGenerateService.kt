package ywcheong.sofia.email

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Service
import ywcheong.sofia.commons.BusinessException
import java.nio.charset.StandardCharsets


@Service
class EmailContentGenerateService(
    private val resourceLoader: ResourceLoader,
) {
    private val logger = KotlinLogging.logger {}

    companion object {
        private const val TEMPLATE_BASE_PATH = "classpath:/static/email-templates/"
        private const val TEMPLATE_EXTENSION = ".html"
        private const val PLACEHOLDER_PREFIX = "{{"
        private const val PLACEHOLDER_SUFFIX = "}}"
        private const val HEADER_TEMPLATE_ID = "_header"
        private const val FOOTER_TEMPLATE_ID = "_footer"
    }

    private val templateCache = mutableMapOf<String, String>()

    /**
     * 이메일 템플릿을 로드하고 placeholder를 치환하여 완성된 HTML 반환
     * header + 본문 + footer 방식으로 구성
     */
    fun generateEmailContent(template: EmailTemplate): String {
        val header = loadTemplate(HEADER_TEMPLATE_ID)
        val body = loadTemplate(template.templateId)
        val footer = loadTemplate(FOOTER_TEMPLATE_ID)
        val placeholders = template.toPlaceholderMap()

        val filledBody = fillPlaceholders(body, placeholders)
        return header + filledBody + footer
    }

    /**
     * 템플릿 파일을 로드 (캐시 사용)
     */
    private fun loadTemplate(templateId: String): String {
        return templateCache.getOrPut(templateId) {
            val resourcePath = "$TEMPLATE_BASE_PATH$templateId$TEMPLATE_EXTENSION"
            val resource = resourceLoader.getResource(resourcePath)

            if (!resource.exists()) {
                throw IllegalStateException("이메일 템플릿을 찾을 수 없습니다: $templateId")
            }

            try {
                resource.inputStream.use { inputStream ->
                    String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
                }
            } catch (e: Exception) {
                logger.error(e) { "이메일 템플릿 로드 실패: $templateId" }
                throw IllegalStateException("이메일 템플릿 로드 중 오류가 발생했습니다: $templateId")
            }
        }
    }

    /**
     * 템플릿의 placeholder를 실제 값으로 치환
     */
    private fun fillPlaceholders(template: String, placeholders: Map<String, String>): String {
        var result = template
        for ((key, value) in placeholders) {
            val placeholder = "$PLACEHOLDER_PREFIX$key$PLACEHOLDER_SUFFIX"
            result = result.replace(placeholder, value)
        }
        return result
    }

    /**
     * 템플릿 캐시 초기화 (테스트용)
     */
    fun clearCache() {
        templateCache.clear()
    }
}
