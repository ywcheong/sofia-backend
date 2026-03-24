package ywcheong.sofia.email

interface EmailSendService {
    fun sendEmail(template: EmailTemplate): EmailSendResult

    data class EmailSendResult(
        val success: Boolean,
        val messageId: String?,
        val errorMessage: String?,
    )
}
