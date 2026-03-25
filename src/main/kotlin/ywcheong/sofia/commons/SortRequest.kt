package ywcheong.sofia.commons

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 정렬 요청을 위한 DTO.
 * 단일 속성에 대한 정렬만 지원하며, null인 경우 기본 정렬을 사용합니다.
 *
 * @param sortField 정렬할 필드명 (null이면 기본 정렬 사용)
 * @param sortDirection 정렬 방향 (null이면 ASC 사용)
 */
@Schema(description = "정렬 요청 파라미터")
data class SortRequest(
    @Schema(description = "정렬 필드명", example = "createdAt", nullable = true)
    val sortField: String? = null,

    @Schema(description = "정렬 방향", example = "DESC", nullable = true)
    val sortDirection: SortDirection? = null,
) {
    /**
     * 정렬 필드와 방향이 모두 지정되지 않은 경우 true를 반환합니다.
     */
    fun isDefault(): Boolean = sortField == null && sortDirection == null

    /**
     * 유효한 정렬 요청인지 검증합니다.
     * sortDirection이 지정되었는데 sortField가 null인 경우 예외를 발생시킵니다.
     */
    fun validate() {
        if (sortDirection != null && sortField == null) {
            throw BusinessException("정렬 방향을 지정하려면 정렬 필드도 함께 지정해야 합니다.")
        }
    }
}

/**
 * 정렬 방향을 나타내는 열거형.
 */
@Schema(description = "정렬 방향")
enum class SortDirection {
    @Schema(description = "오름차순")
    ASC,

    @Schema(description = "내림차순")
    DESC,
    ;

    fun toSortDirection(): org.springframework.data.domain.Sort.Direction =
        when (this) {
            ASC -> org.springframework.data.domain.Sort.Direction.ASC
            DESC -> org.springframework.data.domain.Sort.Direction.DESC
        }
}
