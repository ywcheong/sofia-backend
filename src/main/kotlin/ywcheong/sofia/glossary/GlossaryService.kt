package ywcheong.sofia.glossary

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ywcheong.sofia.commons.BusinessException
import java.util.UUID

@Service
@Transactional
class GlossaryService(
    private val glossaryRepository: GlossaryRepository,
) {
    private val logger = KotlinLogging.logger {}

    data class CreateCommand(
        val koreanTerm: String,
        val englishTerm: String,
    )

    data class UpdateCommand(
        val koreanTerm: String,
        val englishTerm: String,
    )

    data class MappedTerm(
        val koreanTerm: String,
        val englishTerm: String,
    )

    /**
     * UC-008: 전체 사전 항목 조회
     */
    @Transactional(readOnly = true)
    fun findAll(): List<GlossaryEntry> {
        logger.debug { "사전 전체 조회" }
        return glossaryRepository.findAll()
    }

    /**
     * UC-008: 키워드로 사전 항목 검색
     */
    @Transactional(readOnly = true)
    fun search(keyword: String): List<GlossaryEntry> {
        logger.debug { "사전 검색: keyword=$keyword" }
        // 검색어도 공백 제거 + 소문자 변환하여 processedKoreanTerm과 매칭
        val processedKeyword = keyword.filter { !it.isWhitespace() }.lowercase()
        return glossaryRepository.findByProcessedKoreanTermContainingIgnoreCase(processedKeyword)
    }

    /**
     * UC-009: 새 사전 항목 추가
     */
    fun create(command: CreateCommand): GlossaryEntry {
        logger.info { "사전 항목 추가: koreanTerm=${command.koreanTerm}, englishTerm=${command.englishTerm}" }
        validateTermLength(command.koreanTerm, command.englishTerm)

        val entry = GlossaryEntry(
            originalKoreanTerm = command.koreanTerm,
            englishTerm = command.englishTerm,
        )
        return glossaryRepository.save(entry)
    }

    /**
     * UC-009: 기존 사전 항목 수정
     */
    fun update(id: UUID, command: UpdateCommand): GlossaryEntry {
        logger.info { "사전 항목 수정: id=$id" }
        validateTermLength(command.koreanTerm, command.englishTerm)

        val existingEntry = glossaryRepository.findByIdOrNull(id)
            ?: throw BusinessException("존재하지 않는 사전 항목입니다.")

        return GlossaryEntry(
            id = existingEntry.id,
            originalKoreanTerm = command.koreanTerm,
            englishTerm = command.englishTerm,
        ).also { glossaryRepository.save(it) }
    }

    /**
     * UC-009: 사전 항목 삭제
     */
    fun delete(id: UUID) {
        logger.info { "사전 항목 삭제: id=$id" }
        if (!glossaryRepository.existsById(id)) {
            throw BusinessException("존재하지 않는 사전 항목입니다.")
        }
        glossaryRepository.deleteById(id)
    }

    /**
     * UC-010: 텍스트에서 사전 항목 자동 매핑
     */
    @Transactional(readOnly = true)
    fun autoMap(text: String): List<MappedTerm> {
        logger.debug { "사전 자동 매핑: text length=${text.length}" }

        // 텍스트도 공백 제거 + 소문자 변환하여 processedKoreanTerm과 매칭
        val processedText = text.filter { !it.isWhitespace() }.lowercase()

        return glossaryRepository.findAll()
            .filter { processedText.contains(it.processedKoreanTerm) }
            .map { MappedTerm(it.originalKoreanTerm, it.englishTerm) }
    }

    private fun validateTermLength(koreanTerm: String, englishTerm: String) {
        if (koreanTerm.length > 200) {
            throw BusinessException("한국어 용어는 최대 200자까지 입력 가능합니다.")
        }
        if (englishTerm.length > 200) {
            throw BusinessException("영어 대응어는 최대 200자까지 입력 가능합니다.")
        }
    }
}
