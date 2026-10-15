package vn.giapha;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;

/** SWAGGER_ENABLED=false (môi trường thật) phải tắt cả api-docs lẫn swagger-ui. */
@SpringBootTest(properties = "SWAGGER_ENABLED=false")
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class SwaggerDisabledTest {
    @Autowired Environment env;
    @Autowired MockMvc mvc;

    @Test
    void envVarBindsSpringdocProperties() {
        assertEquals("false", env.getProperty("springdoc.api-docs.enabled"));
        assertEquals("false", env.getProperty("springdoc.swagger-ui.enabled"));
    }

    @Test
    void apiDocsNotServed() throws Exception {
        assertNotEquals(200, mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getStatus());
    }
}
