package ywcheong.sofia.email

/**
 * 이메일 템플릿 인터페이스
 * templateId로 resources/static/email-templates/{templateId}.html 파일을 찾아 placeholder를 채워 발송
 */
interface EmailTemplate {
    val templateId: String
    val subject: String
    val recipientEmail: String
    val recipientName: String
    val unsubscribeToken: String

    /**
     * 템플릿의 placeholder를 치환하기 위한 데이터 맵
     * key: placeholder 이름 (예: "taskTitle")
     * value: 치환할 값
     */
    fun toPlaceholderMap(): Map<String, String>
}
