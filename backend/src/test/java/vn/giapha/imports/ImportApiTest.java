package vn.giapha.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import vn.giapha.PostgresTestConfig;
import vn.giapha.auth.Role;
import vn.giapha.support.TestUsers;
import vn.giapha.support.XlsxBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestConfig.class, TestUsers.class})
class ImportApiTest {
    @Autowired MockMvc mvc;
    @Autowired TestUsers users;
    @Autowired JdbcTemplate jdbc;

    static List<Object> p(Object... v) { return Arrays.asList(v); }
    static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    MockMultipartFile file(byte[] bytes) { return new MockMultipartFile("file", "du-lieu.xlsx", XLSX, bytes); }
    byte[] good() {
        return new XlsxBuilder().sheet("Người", List.of("Mã", "Họ và tên", "Giới tính", "Ngày sinh"),
            List.of(p("A1", "Người API 1", "Nam", "1950"), p("A2", "Người API 2", "Nữ", "1952"))).bytes();
    }
    byte[] bad() {
        return new XlsxBuilder().sheet("Người", List.of("Mã", "Họ và tên", "Giới tính", "Ngày sinh"),
            List.of(p("A1", "Người API 1", "Nam", "1950"), p("A2", "", "Nữ", "1952"))).bytes();
    }

    @Test
    void permissionMatrix() throws Exception {
        mvc.perform(get("/api/v1/import/template")).andExpect(status().isUnauthorized());
        for (Role r : new Role[]{Role.VIEWER, Role.EDITOR}) {
            mvc.perform(get("/api/v1/import/template").header("Authorization", users.bearer(r))).andExpect(status().isForbidden());
            mvc.perform(multipart(HttpMethod.POST, "/api/v1/import/excel").file(file(good())).header("Authorization", users.bearer(r))).andExpect(status().isForbidden());
        }
        mvc.perform(multipart(HttpMethod.POST, "/api/v1/import/excel").file(file(good()))).andExpect(status().isUnauthorized());
    }

    @Test
    void adminDownloadsTemplate() throws Exception {
        var res = mvc.perform(get("/api/v1/import/template").header("Authorization", users.bearer(Role.ADMIN)))
            .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("mau-nhap-gia-pha.xlsx")))
            .andReturn().getResponse();
        assertThat(res.getContentAsByteArray().length).isGreaterThan(1000);
        assertThat(res.getContentType()).isEqualTo(XLSX);
    }

    @Test
    void dryRunIsDefaultAndDoesNotSave() throws Exception {
        int before = jdbc.queryForObject("SELECT count(*) FROM person WHERE full_name LIKE 'Người API%'", Integer.class);
        mvc.perform(multipart(HttpMethod.POST, "/api/v1/import/excel").file(file(good())).header("Authorization", users.bearer(Role.ADMIN)))
           .andExpect(status().isOk()).andExpect(jsonPath("$.dryRun").value(true)).andExpect(jsonPath("$.persons").value(2));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM person WHERE full_name LIKE 'Người API%'", Integer.class)).isEqualTo(before);
    }

    @Test
    void realImportSaves() throws Exception {
        int before = jdbc.queryForObject("SELECT count(*) FROM person WHERE full_name LIKE 'Người API%'", Integer.class);
        mvc.perform(multipart(HttpMethod.POST, "/api/v1/import/excel").file(file(good())).param("dryRun", "false").header("Authorization", users.bearer(Role.ADMIN)))
           .andExpect(status().isOk()).andExpect(jsonPath("$.dryRun").value(false)).andExpect(jsonPath("$.errors.length()").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM person WHERE full_name LIKE 'Người API%'", Integer.class)).isEqualTo(before + 2);
    }

    @Test
    void missingFilePartIs400() throws Exception {
        mvc.perform(multipart(HttpMethod.POST, "/api/v1/import/excel").header("Authorization", users.bearer(Role.ADMIN)))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void nonMultipartIs415() throws Exception {
        mvc.perform(post("/api/v1/import/excel").contentType("application/json").content("{}").header("Authorization", users.bearer(Role.ADMIN)))
           .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void emptyFileIs400() throws Exception {
        mvc.perform(multipart(HttpMethod.POST, "/api/v1/import/excel").file(file(new byte[0])).header("Authorization", users.bearer(Role.ADMIN)))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void badDryRunValueIs400() throws Exception {
        mvc.perform(multipart(HttpMethod.POST, "/api/v1/import/excel").file(file(good())).param("dryRun", "abc").header("Authorization", users.bearer(Role.ADMIN)))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void dryRunWithErrorsIs200ButRealImportIs422WithReport() throws Exception {
        mvc.perform(multipart(HttpMethod.POST, "/api/v1/import/excel").file(file(bad())).param("dryRun", "true").header("Authorization", users.bearer(Role.ADMIN)))
           .andExpect(status().isOk()).andExpect(jsonPath("$.errors[0].row").value(3)).andExpect(jsonPath("$.errors[0].column").value("Họ và tên"));
        mvc.perform(multipart(HttpMethod.POST, "/api/v1/import/excel").file(file(bad())).param("dryRun", "false").header("Authorization", users.bearer(Role.ADMIN)))
           .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("IMPORT_INVALID"))
           .andExpect(jsonPath("$.details.errors[0].row").value(3));
    }

    @Test
    void garbageFileIs400() throws Exception {
        mvc.perform(multipart(HttpMethod.POST, "/api/v1/import/excel").file(file("not excel".getBytes())).header("Authorization", users.bearer(Role.ADMIN)))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
