package vn.giapha.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.PostgresTestConfig;
import vn.giapha.auth.*;
import vn.giapha.support.TestUsers;
import vn.giapha.support.TreeFixture;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestConfig.class, TestUsers.class, TreeFixture.class})
@Transactional
class AuditApiTest {
    @Autowired MockMvc mvc;
    @Autowired TestUsers users;
    @Autowired TreeFixture fixture;
    @Autowired ObjectMapper om;

    static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    long createPerson(String bearer, String name) throws Exception {
        String r = mvc.perform(post("/api/v1/persons").header("Authorization", bearer).contentType(JSON)
            .content("{\"fullName\":\"" + name + "\",\"gender\":\"M\",\"alive\":true}")).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return om.readTree(r).get("id").asLong();
    }

    @Test
    void permissionMatrix() throws Exception {
        mvc.perform(get("/api/v1/audit?entityId=1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/audit?entityId=1").header("Authorization", users.bearer(Role.VIEWER))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/audit?entityId=1").header("Authorization", users.bearer(Role.EDITOR))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/audit?entityId=1").header("Authorization", users.bearer(Role.ADMIN))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/audit").header("Authorization", users.bearer(Role.EDITOR))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/audit").header("Authorization", users.bearer(Role.ADMIN))).andExpect(status().isOk());
    }

    @Test
    void personHistoryShowsCreateUpdateWithUserAndChanges() throws Exception {
        AppUser editor = users.create(Role.EDITOR);
        String auth = users.bearerFor(editor);
        long id = createPerson(auth, "Trần Ban Đầu");
        long v = 0;
        mvc.perform(put("/api/v1/persons/" + id).header("Authorization", auth).contentType(JSON)
            .content("{\"fullName\":\"Trần Đã Sửa\",\"gender\":\"M\",\"alive\":true,\"version\":" + v + "}")).andExpect(status().isOk());
        String body = mvc.perform(get("/api/v1/audit?entityId=" + id).header("Authorization", auth)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        JsonNode list = om.readTree(body);
        assertThat(list).hasSize(2);
        JsonNode update = list.get(0);               // mới nhất trước
        assertThat(update.get("action").asText()).isEqualTo("UPDATE");
        assertThat(update.get("username").asText()).isEqualTo(editor.getUsername());
        assertThat(update.get("summary").asText()).isEqualTo("Sửa hồ sơ «Trần Đã Sửa»");
        assertThat(update.get("changes").get(0).get("label").asText()).isEqualTo("Họ tên");
        assertThat(update.get("changes").get(0).get("from").asText()).isEqualTo("Trần Ban Đầu");
        assertThat(update.get("revertible").asBoolean()).isTrue();
        assertThat(list.get(1).get("summary").asText()).isEqualTo("Tạo người «Trần Ban Đầu»");
    }

    @Test
    void relationEntriesAppearInBothPersonsHistoriesWithNames() throws Exception {
        var ids = fixture.create();
        String auth = users.bearer(Role.EDITOR);
        long kid = createPerson(auth, "Cháu Mới");
        mvc.perform(post("/api/v1/relations/parent-child").header("Authorization", auth).contentType(JSON)
            .content("{\"parentId\":" + ids.a1() + ",\"childId\":" + kid + "}")).andExpect(status().isCreated());
        for (long who : new long[]{ids.a1(), kid}) {
            mvc.perform(get("/api/v1/audit?entityId=" + who).header("Authorization", auth))
               .andExpect(jsonPath("$[0].summary").value("Thêm quan hệ: Nguyễn Văn Một là cha/mẹ của Cháu Mới"));
        }
        // người không liên quan không thấy
        mvc.perform(get("/api/v1/audit?entityId=" + ids.c()).header("Authorization", auth)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void paginationWithBeforeIdAndLimit() throws Exception {
        String auth = users.bearer(Role.EDITOR);
        long id = createPerson(auth, "P");
        for (int i = 0; i < 4; i++) {
            long ver = om.readTree(mvc.perform(get("/api/v1/persons/" + id).header("Authorization", auth)).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).get("version").asLong();
            mvc.perform(put("/api/v1/persons/" + id).header("Authorization", auth).contentType(JSON)
                .content("{\"fullName\":\"P" + i + "\",\"gender\":\"M\",\"alive\":true,\"version\":" + ver + "}")).andExpect(status().isOk());
        }
        JsonNode page1 = om.readTree(mvc.perform(get("/api/v1/audit?entityId=" + id + "&limit=2").header("Authorization", auth)).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(page1).hasSize(2);
        long last = page1.get(1).get("id").asLong();
        JsonNode page2 = om.readTree(mvc.perform(get("/api/v1/audit?entityId=" + id + "&limit=10&beforeId=" + last).header("Authorization", auth)).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(page2).hasSize(3);                   // 5 mục tổng (1 tạo + 4 sửa) − 2
        assertThat(page2.get(0).get("id").asLong()).isLessThan(last);
        mvc.perform(get("/api/v1/audit?entityId=" + id + "&limit=0").header("Authorization", auth)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }
}
