package ywcheong.sofia.user.auth

enum class SofiaPermission(val springRoleName: String) {
    STUDENT_LEVEL("ROLE_STUDENT"),
    ADMIN_LEVEL("ROLE_ADMIN"),
    KAKAO_ENDPOINT("ROLE_KAKAO")
}