package ywcheong.sofia.glossary

import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface GlossaryRepository : JpaRepository<GlossaryEntry, UUID> {
    fun findByKoreanTermContainingIgnoreCase(koreanTerm: String): List<GlossaryEntry>
}
