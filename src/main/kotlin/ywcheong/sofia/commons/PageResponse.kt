package ywcheong.sofia.commons

import org.springframework.data.domain.Page

/**
 * Spring Data의 Page 인터페이스를 대체하는 역직렬화에 안전한 data class.
 * JSON 직렬화/역직렬화 시 Page 구현체의 복잡한 내부 구조로 인한 문제를 방지합니다.
 */
data class PageResponse<T>(
    val content: List<T>,
    val totalPages: Int,
    val totalElements: Long,
    val number: Int,
    val size: Int,
    val numberOfElements: Int,
    val first: Boolean,
    val last: Boolean,
    val empty: Boolean,
) {
    companion object {
        /**
         * Spring Data Page를 PageResponse로 변환합니다.
         *
         * @param page 변환할 Page 객체
         * @return 역직렬화에 안전한 PageResponse 객체
         */
        fun <T : Any> from(page: Page<T>): PageResponse<T> =
            PageResponse(
                content = page.content,
                totalPages = page.totalPages,
                totalElements = page.totalElements,
                number = page.number,
                size = page.size,
                numberOfElements = page.numberOfElements,
                first = page.isFirst,
                last = page.isLast,
                empty = page.isEmpty,
            )
    }
}
