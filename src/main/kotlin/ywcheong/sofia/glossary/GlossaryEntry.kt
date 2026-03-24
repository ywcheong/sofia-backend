package ywcheong.sofia.glossary

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import java.util.UUID

@Entity
class GlossaryEntry(
    id: UUID = UUID.randomUUID(),
    koreanTerm: String,
    englishTerm: String,
) {
    @Id
    val id: UUID = id

    @Column(length = 200, nullable = false)
    var koreanTerm: String = koreanTerm
        protected set

    @Column(length = 200, nullable = false)
    var englishTerm: String = englishTerm
        protected set
}
