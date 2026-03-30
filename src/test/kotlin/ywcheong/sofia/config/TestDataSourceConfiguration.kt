package ywcheong.sofia.config

import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.testcontainers.containers.PostgreSQLContainer

@Configuration
class TestDataSourceConfiguration {
    @Bean
    @ServiceConnection
    fun postgresqlContainer(): PostgreSQLContainer<*> =
        PostgreSQLContainer("postgres:17").withReuse(true)
}