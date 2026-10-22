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
@Import({PostgresTestConfig.class, GlobalExceptionHandlerTest.EchoController.class, GlobalExceptionHandlerTest.BoomController.class})
class GlobalExceptionHandlerTest {
    @Autowired MockMvc mvc;

    /** Endpoint tối thiểu đọc JSON body; POST vào path không tồn tại trả 404 trước khi đọc body. */
    @RestController
    static class EchoController {
        @PostMapping("/api/v1/test-echo")
        Map<String, Object> echo(@RequestBody Map<String, Object> body) { return body; }
    }

    @RestController
    static class BoomController {
        @org.springframework.web.bind.annotation.GetMapping("/api/v1/test-boom")
        String boom() { throw new IllegalStateException("chi tiết nội bộ bí mật"); }
        @org.springframework.web.bind.annotation.GetMapping("/api/v1/test-integrity")
        String integrity() { throw new org.springframework.dao.DataIntegrityViolationException("duplicate key"); }
    }

    @Test
    @WithMockUser
    void unexpectedExceptionIs500JsonNot401() throws Exception {
        mvc.perform(get("/api/v1/test-boom"))
           .andExpect(status().isInternalServerError())
           .andExpect(jsonPath("$.code").value("INTERNAL"))
           .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("bí mật"))));
    }

    @Test
    @WithMockUser
    void dataIntegrityViolationIs409() throws Exception {
        mvc.perform(get("/api/v1/test-integrity"))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @WithMockUser
    void springOwn4xxKeepsItsStatus() throws Exception {
        mvc.perform(post("/api/v1/test-boom")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void anonymousStillGets401() throws Exception {
        mvc.perform(get("/api/v1/test-boom")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
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
