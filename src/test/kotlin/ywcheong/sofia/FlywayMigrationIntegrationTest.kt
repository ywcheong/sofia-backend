package ywcheong.sofia

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.ThreadLocalRandom
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest
class FlywayMigrationIntegrationTest {

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `flyway applies baseline schema and seeds single-row tables`() {
        val tableCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name IN (
                'users',
                'participation_applications',
                'works',
                'dictionary_entries',
                'adjustments',
                'warnings',
                'system_phase',
                'auth_tokens',
                'email_logs',
                'assignment_cursor',
                'kakao_request_logs'
              )
            """.trimIndent(),
            Int::class.java,
        )

        assertThat(tableCount).isEqualTo(11)

        val systemPhase = jdbcTemplate.queryForMap(
            "SELECT id, phase, phase_started_at FROM system_phase WHERE id = 1"
        )
        assertThat(systemPhase["id"]).isEqualTo(1L)
        assertThat(systemPhase["phase"]).isEqualTo("PHASE_0")
        val phaseStartedAt = systemPhase["phase_started_at"].toString()
        assertThat(phaseStartedAt).isEqualTo(LocalDate.now(ZoneOffset.UTC).toString())

        val assignmentCursor = jdbcTemplate.queryForMap(
            "SELECT id, last_assigned_user_id FROM assignment_cursor WHERE id = 1"
        )
        assertThat(assignmentCursor["id"]).isEqualTo(1L)
        assertThat(assignmentCursor["last_assigned_user_id"]).isNull()
    }

    @Test
    fun `users uniqueness allows reinsert after soft delete`() {
        val studentId = "T" + ThreadLocalRandom.current().nextInt(10000, 99999)
        val botUserKey = "bot-$studentId"
        val plusfriendUserKey = "pf-$studentId"
        val timestamp = OffsetDateTime.now(ZoneOffset.UTC)

        val firstInsert = jdbcTemplate.update(
            """
            INSERT INTO users (
                student_id,
                name,
                kakao_bot_user_key,
                kakao_plusfriend_user_key,
                created_at,
                updated_at,
                deleted,
                deleted_at
            ) VALUES (?, ?, ?, ?, ?, ?, FALSE, NULL)
            """.trimIndent(),
            studentId,
            "tester",
            botUserKey,
            plusfriendUserKey,
            timestamp,
            timestamp,
        )
        assertThat(firstInsert).isEqualTo(1)

        val softDelete = jdbcTemplate.update(
            """
            UPDATE users
            SET deleted = TRUE,
                deleted_at = ?,
                updated_at = ?
            WHERE student_id = ?
              AND deleted = FALSE
            """.trimIndent(),
            timestamp.plusSeconds(1),
            timestamp.plusSeconds(1),
            studentId,
        )
        assertThat(softDelete).isEqualTo(1)

        val secondInsert = jdbcTemplate.update(
            """
            INSERT INTO users (
                student_id,
                name,
                kakao_bot_user_key,
                kakao_plusfriend_user_key,
                created_at,
                updated_at,
                deleted,
                deleted_at
            ) VALUES (?, ?, ?, ?, ?, ?, FALSE, NULL)
            """.trimIndent(),
            studentId,
            "tester2",
            botUserKey,
            plusfriendUserKey,
            timestamp.plusSeconds(2),
            timestamp.plusSeconds(2),
        )
        assertThat(secondInsert).isEqualTo(1)

        val activeUserCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE student_id = ? AND deleted = FALSE",
            Int::class.java,
            studentId,
        )
        val deletedUserCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE student_id = ? AND deleted = TRUE",
            Int::class.java,
            studentId,
        )

        assertThat(activeUserCount).isEqualTo(1)
        assertThat(deletedUserCount).isEqualTo(1)
    }
}
