package ywcheong.sofia.user.auth

enum class SofiaActor(vararg val permissions: SofiaPermission) {
    NONADMIN_STUDENT(SofiaPermission.STUDENT_LEVEL),
    ADMIN_STUDENT(SofiaPermission.STUDENT_LEVEL, SofiaPermission.ADMIN_LEVEL),
    KAKAO_SKILL_SERVER(SofiaPermission.KAKAO_ENDPOINT)
}