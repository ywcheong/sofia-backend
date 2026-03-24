package ywcheong.sofia.phase

enum class SystemPhase(val displayName: String) {
    DEACTIVATION("비활성"),
    RECRUITMENT("모집"),
    TRANSLATION("번역"),
    SETTLEMENT("정산"),
    ;

    val nextPhase: SystemPhase
        get() = when (this) {
            DEACTIVATION -> RECRUITMENT
            RECRUITMENT -> TRANSLATION
            TRANSLATION -> SETTLEMENT
            SETTLEMENT -> DEACTIVATION
        }
}