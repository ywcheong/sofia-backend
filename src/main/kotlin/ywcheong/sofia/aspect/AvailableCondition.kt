package ywcheong.sofia.aspect

import ywcheong.sofia.phase.SystemPhase
import ywcheong.sofia.user.auth.SofiaPermission

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
@MustBeDocumented
annotation class AvailableCondition(
    /** OR 조건 : 이들 중 하나의 페이즈기만 하면 통과, 빈 리스트는 항상 허용이라는 뜻 */
    val phases: Array<SystemPhase>,
    /** OR 조건 : 이들 중 하나의 권한만 있어도 통과, 빈 리스트는 항상 허용이라는 뜻 */
    val permissions: Array<SofiaPermission>,
)