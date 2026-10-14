package vn.giapha.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
import vn.giapha.PostgresTestConfig;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestConfig.class, GlobalExceptionHandlerTest.EchoController.class})
class GlobalExceptionHandlerTest {
    @Autowired MockMvc mvc;

    /** Endpoint tối thiểu đọc JSON body; POST vào path không tồn tại trả 404 trước khi đọc body. */
    @RestController
    static class EchoController {
        @PostMapping("/api/v1/test-echo")
        Map<String, Object> echo(@RequestBody Map<String, Object> body) { return body; }
    }

    @Test
    @WithMockUser
    void unknownPathReturnsJsonNotFound() throws Exception {
        mvc.perform(get("/api/v1/khong-ton-tai"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @WithMockUser
    void malformedJsonReturnsValidationFailed() throws Exception {
        mvc.perform(post("/api/v1/test-echo").contentType(MediaType.APPLICATION_JSON).content("{bad"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
