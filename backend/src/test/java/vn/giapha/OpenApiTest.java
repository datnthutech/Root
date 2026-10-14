package vn.giapha;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class OpenApiTest {
    @Autowired MockMvc mvc;

    @Test
    void apiDocsListTreeEndpoints() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
           .andExpect(jsonPath("$.paths['/api/v1/clans/{id}/graph']").exists())
           .andExpect(jsonPath("$.paths['/api/v1/persons/{id}/family']").exists())
           .andExpect(jsonPath("$.paths['/api/v1/persons/search']").exists())
           .andExpect(jsonPath("$.paths['/api/v1/persons/{id}/focus']").exists());
    }
}
