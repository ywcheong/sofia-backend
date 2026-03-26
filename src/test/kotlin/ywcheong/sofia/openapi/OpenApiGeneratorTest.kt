package ywcheong.sofia.openapi

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.io.File

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("OpenAPI 생성")
class OpenApiGeneratorTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    @DisplayName("GET /v3/api-docs - OpenAPI 스펙 생성")
    fun `OpenAPI JSON 생성`() {
        val result = mockMvc.get("/v3/api-docs")
            .andExpect { status { isOk() } }
            .andReturn()

        val jsonContent = result.response.contentAsString
        val outputFile = File("openapi.json")
        outputFile.writeText(jsonContent)
    }
}
