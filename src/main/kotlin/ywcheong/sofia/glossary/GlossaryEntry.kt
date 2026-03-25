package ywcheong.sofia.glossary

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import java.util.UUID

@Entity
class GlossaryEntry(
    id: UUID = UUID.randomUUID(),
    originalKoreanTerm: String,
    englishTerm: String,
) {
    @Id
    val id: UUID = id

    @Column(length = 200, nullable = false)
    var originalKoreanTerm: String = originalKoreanTerm
        protected set

    @Column(length = 200, nullable = false)
    var processedKoreanTerm: String = originalKoreanTerm
        .filter { !it.isWhitespace() }
        .lowercase()
        protected set

    @Column(length = 200, nullable = false)
    var englishTerm: String = englishTerm
        protected set
}
