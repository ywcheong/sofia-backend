package ywcheong.sofia.glossary

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ywcheong.sofia.aspect.AvailableCondition
import ywcheong.sofia.phase.SystemPhase
import ywcheong.sofia.user.auth.SofiaPermission
import java.util.*

@RestController
@RequestMapping("/glossary")
@Tag(name = "Glossary", description = "번역 용어 사전 조회, 추가, 수정, 삭제 및 자동 매핑 API")
class GlossaryController(
    private val glossaryService: GlossaryService,
) {
    private val logger = KotlinLogging.logger {}

    // === Request/Response DTOs ===

    data class CreateRequest(
        val koreanTerm: String,
        val englishTerm: String,
    ) {
        fun toCommand() = GlossaryService.CreateCommand(
            koreanTerm = koreanTerm,
            englishTerm = englishTerm,
        )
    }

    data class UpdateRequest(
        val koreanTerm: String,
        val englishTerm: String,
    ) {
        fun toCommand() = GlossaryService.UpdateCommand(
            koreanTerm = koreanTerm,
            englishTerm = englishTerm,
        )
    }

    data class EntryResponse(
        val id: UUID,
        val koreanTerm: String,
        val englishTerm: String,
    ) {
        companion object {
            fun from(entry: GlossaryEntry) = EntryResponse(
                id = entry.id,
                koreanTerm = entry.originalKoreanTerm,
                englishTerm = entry.englishTerm,
            )
        }
    }

    data class AutoMapRequest(
        val text: String,
    )

    data class AutoMapResponse(
        val koreanTerm: String,
        val englishTerm: String,
    ) {
        companion object {
            fun from(mappedTerm: GlossaryService.MappedTerm) = AutoMapResponse(
                koreanTerm = mappedTerm.koreanTerm,
                englishTerm = mappedTerm.englishTerm,
            )
        }
    }

    // === UC-008: 사전 조회 ===

    @AvailableCondition(
        phases = [SystemPhase.RECRUITMENT, SystemPhase.TRANSLATION, SystemPhase.SETTLEMENT],
        permissions = []
    )
    @GetMapping
    @Operation(summary = "용어 사전 조회", description = "keyword 있으면 부분검색, 없으면 전체조회")
    fun findAll(@RequestParam(required = false) keyword: String?): List<EntryResponse> {
        logger.info { "사전 조회 요청: keyword=$keyword" }

        val entries = if (keyword.isNullOrBlank()) {
            glossaryService.findAll()
        } else {
            glossaryService.search(keyword)
        }

        return entries.map { EntryResponse.from(it) }
    }

    // === UC-009: 사전 수정 ===

    @AvailableCondition(
        phases = [SystemPhase.RECRUITMENT, SystemPhase.TRANSLATION, SystemPhase.SETTLEMENT],
        permissions = [SofiaPermission.ADMIN_LEVEL]
    )
    @PostMapping
    @Operation(summary = "용어 추가", description = "중복된 한국어 용어가 있으면 400 에러")
    fun create(@RequestBody request: CreateRequest): EntryResponse {
        logger.info { "사전 항목 추가 요청: koreanTerm=${request.koreanTerm}" }
        val entry = glossaryService.create(request.toCommand())
        return EntryResponse.from(entry)
    }

    @AvailableCondition(phases = [SystemPhase.RECRUITMENT, SystemPhase.TRANSLATION, SystemPhase.SETTLEMENT], permissions = [SofiaPermission.ADMIN_LEVEL])
    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody request: UpdateRequest): EntryResponse {
        logger.info { "사전 항목 수정 요청: id=$id" }
        val entry = glossaryService.update(id, request.toCommand())
        return EntryResponse.from(entry)
    }

    @AvailableCondition(phases = [SystemPhase.RECRUITMENT, SystemPhase.TRANSLATION, SystemPhase.SETTLEMENT], permissions = [SofiaPermission.ADMIN_LEVEL])
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID) {
        logger.info { "사전 항목 삭제 요청: id=$id" }
        glossaryService.delete(id)
    }

    // === UC-010: 사전 자동 매핑 ===

    @AvailableCondition(phases = [SystemPhase.RECRUITMENT, SystemPhase.TRANSLATION, SystemPhase.SETTLEMENT], permissions = [])
    @PostMapping("/auto-map")
    @Operation(summary = "용어 자동 매핑", description = "대소문자/공백 무시하고 매핑, 매칭된 용어만 반환")
    fun autoMap(@RequestBody request: AutoMapRequest): List<AutoMapResponse> {
        logger.info { "사전 자동 매핑 요청: text length=${request.text.length}" }
        val mappedTerms = glossaryService.autoMap(request.text)
        return mappedTerms.map { AutoMapResponse.from(it) }
    }
}
