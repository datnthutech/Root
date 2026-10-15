package vn.giapha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import vn.giapha.auth.Role;
import vn.giapha.support.InMemoryPhotoStorage;
import vn.giapha.support.TestUsers;
import vn.giapha.support.XlsxBuilder;

/** Hành trình đầu-cuối qua API thật (không @Transactional: mỗi bước commit như môi trường chạy thật). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestConfig.class, TestUsers.class, InMemoryPhotoStorage.class})
class FullJourneyTest {
    @Autowired MockMvc mvc;
    @Autowired TestUsers users;
    @Autowired ObjectMapper om;
    @Autowired InMemoryPhotoStorage storage;

    static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    static List<Object> p(Object... v) { return Arrays.asList(v); }

    JsonNode body(ResultActions r) throws Exception { return om.readTree(r.andReturn().getResponse().getContentAsByteArray()); }

    @Test
    void registerApproveImportEditAuditRevertLogout() throws Exception {
        String admin = users.bearer(Role.ADMIN);
        String user = "ed" + UUID.randomUUID().toString().substring(0, 8);

        // 1. đăng ký → chờ duyệt, chưa đăng nhập được
        long userId = body(mvc.perform(post("/api/v1/auth/register").contentType(JSON)
            .content("{\"username\":\"" + user + "\",\"password\":\"password123\",\"displayName\":\"Biên Tập\"}")).andExpect(status().isCreated())).get("id").asLong();
        mvc.perform(post("/api/v1/auth/login").contentType(JSON).content("{\"username\":\"" + user + "\",\"password\":\"password123\"}")).andExpect(status().isForbidden());

        // 2. admin duyệt thành EDITOR
        mvc.perform(put("/api/v1/admin/users/" + userId).header("Authorization", admin).contentType(JSON).content("{\"status\":\"ACTIVE\",\"role\":\"EDITOR\"}")).andExpect(status().isOk());

        // 3. admin nhập Excel thật: chạy thử (dryRun) không lưu, rồi nhập thật
        String tag = UUID.randomUUID().toString().substring(0, 6);
        byte[] xlsx = new XlsxBuilder()
            .sheet("Người", List.of("Mã", "Họ và tên", "Giới tính", "Ngày sinh", "Còn sống", "Ngày mất"), List.of(
                p("P1", "Tổ " + tag, "Nam", "1880", "Không", "1950"), p("P2", "Vợ " + tag, "Nữ", "1885", "Không", "1960"), p("P3", "Con " + tag, "Nam", "1910", "Có", "")))
            .sheet("Hôn phối", List.of("Mã hôn phối", "Mã người A", "Mã người B"), List.of(p("H1", "P1", "P2")))
            .sheet("Quan hệ", List.of("Mã cha/mẹ", "Mã con", "Mã hôn phối"), List.of(p("P1", "P3", "H1"), p("P2", "P3", "H1")))
            .sheet("Dòng họ", List.of("Tên dòng họ", "Mã thủy tổ"), List.of(p("Họ " + tag, "P1"))).bytes();
        var dry = body(mvc.perform(multipart(HttpMethod.POST, "/api/v1/import/excel").file(new MockMultipartFile("file", "a.xlsx", XLSX, xlsx))
            .param("dryRun", "true").header("Authorization", admin)).andExpect(status().isOk()));
        assertThat(dry.get("dryRun").asBoolean()).isTrue();
        assertThat(dry.get("errors")).isEmpty();
        assertThat(dry.get("persons").asInt()).isEqualTo(3);
        var imp = body(mvc.perform(multipart(HttpMethod.POST, "/api/v1/import/excel").file(new MockMultipartFile("file", "a.xlsx", XLSX, xlsx))
            .param("dryRun", "false").header("Authorization", admin)).andExpect(status().isOk()));
        assertThat(imp.get("errors")).isEmpty();
        long clanId = imp.get("clanId").asLong();
        // nhập lại cùng tệp: tên dòng họ đã có với thủy tổ khác → bị từ chối, không nhân đôi
        mvc.perform(multipart(HttpMethod.POST, "/api/v1/import/excel").file(new MockMultipartFile("file", "a.xlsx", XLSX, xlsx))
            .param("dryRun", "false").header("Authorization", admin)).andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("IMPORT_INVALID")).andExpect(jsonPath("$.details.errors[0].code").value("CLAN_EXISTS"));

        // 4. editor đăng nhập thật và xem cây
        var login = mvc.perform(post("/api/v1/auth/login").contentType(JSON).content("{\"username\":\"" + user + "\",\"password\":\"password123\"}")).andExpect(status().isOk()).andReturn();
        String editor = "Bearer " + om.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
        Cookie refresh = login.getResponse().getCookie("refresh_token");
        assertThat(refresh).isNotNull();
        var graph = body(mvc.perform(get("/api/v1/clans/" + clanId + "/graph").header("Authorization", editor)).andExpect(status().isOk()));
        assertThat(graph.get("persons")).hasSize(3);
        assertThat(graph.get("unions")).hasSize(1);
        assertThat(graph.get("links")).hasSize(2);
        long founder = graph.get("rootPersonId").asLong();
        long importedChild = 0;
        for (JsonNode n : graph.get("persons")) if (n.get("name").asText().startsWith("Con ")) importedChild = n.get("id").asLong();
        assertThat(founder).isPositive();
        assertThat(importedChild).isPositive();

        // 5. editor tạo dâu, hôn phối với người đã nhập, rồi tạo cháu gắn làm con của cặp; tải ảnh lên cho cháu
        long spouse = createPerson(editor, "Dâu " + tag, "F");
        long unionId = body(mvc.perform(post("/api/v1/relations/unions").header("Authorization", editor).contentType(JSON)
            .content("{\"partnerAId\":" + importedChild + ",\"partnerBId\":" + spouse + "}")).andExpect(status().isCreated())).get("id").asLong();
        long created = body(mvc.perform(post("/api/v1/persons").header("Authorization", editor).contentType(JSON)
            .content("{\"fullName\":\"Cháu " + tag + "\",\"gender\":\"F\",\"birthDate\":\"1940-05-06\",\"birthPrecision\":\"DAY\"}")).andExpect(status().isCreated())).get("id").asLong();
        var kids = body(mvc.perform(post("/api/v1/relations/children").header("Authorization", editor).contentType(JSON)
            .content("{\"parentId\":" + importedChild + ",\"childId\":" + created + ",\"unionId\":" + unionId + "}")).andExpect(status().isCreated()));
        assertThat(kids.get("links")).hasSize(2);
        var graph2 = body(mvc.perform(get("/api/v1/clans/" + clanId + "/graph").header("Authorization", editor)).andExpect(status().isOk()));
        assertThat(graph2.get("persons")).hasSize(5);
        assertThat(graph2.get("unions")).hasSize(2);

        var img = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB), "png", img);
        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/persons/{id}/photo", created).file(new MockMultipartFile("file", "a.png", "image/png", img.toByteArray()))
            .header("Authorization", editor)).andExpect(status().isOk()).andExpect(jsonPath("$.photoKey").isNotEmpty());
        mvc.perform(get("/api/v1/persons/" + created + "/photo?size=96&v=x").header("Authorization", editor)).andExpect(status().isOk());
        assertThat(storage.data).isNotEmpty();

        // 6. sửa người mình tạo được; sửa người đã nhập thì 403
        long v = body(mvc.perform(get("/api/v1/persons/" + created).header("Authorization", editor))).get("version").asLong();
        mvc.perform(put("/api/v1/persons/" + created).header("Authorization", editor).contentType(JSON)
            .content("{\"fullName\":\"Cháu Đã Sửa " + tag + "\",\"gender\":\"F\",\"alive\":true,\"version\":" + v + "}")).andExpect(status().isOk());
        long vImp = body(mvc.perform(get("/api/v1/persons/" + importedChild).header("Authorization", editor))).get("version").asLong();
        mvc.perform(put("/api/v1/persons/" + importedChild).header("Authorization", editor).contentType(JSON)
            .content("{\"fullName\":\"Không được\",\"gender\":\"M\",\"alive\":true,\"version\":" + vImp + "}")).andExpect(status().isForbidden());

        // 7. lịch sử có dòng UPDATE; chỉ admin khôi phục
        var history = body(mvc.perform(get("/api/v1/audit?entityId=" + created).header("Authorization", editor)).andExpect(status().isOk()));
        long updateAudit = 0;
        for (JsonNode e : history) if ("UPDATE".equals(e.get("action").asText()) && "PERSON".equals(e.get("entityType").asText())) { updateAudit = e.get("id").asLong(); break; }
        assertThat(updateAudit).isPositive();
        mvc.perform(post("/api/v1/audit/" + updateAudit + "/revert").header("Authorization", editor)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/audit/" + updateAudit + "/revert").header("Authorization", admin)).andExpect(status().isNoContent());
        assertThat(body(mvc.perform(get("/api/v1/persons/" + created).header("Authorization", admin))).get("fullName").asText()).isEqualTo("Cháu " + tag);

        // 8. viewer xem được nhưng không ghi được (403 trước cả khi kiểm tra body)
        String viewer = users.bearer(Role.VIEWER);
        mvc.perform(get("/api/v1/clans/" + clanId + "/graph").header("Authorization", viewer)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/persons").header("Authorization", viewer).contentType(JSON).content("{\"fullName\":\"X\",\"gender\":\"M\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/persons").header("Authorization", viewer).contentType(JSON).content("{}")).andExpect(status().isForbidden());

        // 9. đăng xuất vô hiệu hoá refresh token
        mvc.perform(post("/api/v1/auth/refresh").cookie(refresh)).andExpect(status().isOk());
        var login2 = mvc.perform(post("/api/v1/auth/login").contentType(JSON).content("{\"username\":\"" + user + "\",\"password\":\"password123\"}")).andExpect(status().isOk()).andReturn();
        Cookie refresh2 = login2.getResponse().getCookie("refresh_token");
        mvc.perform(post("/api/v1/auth/logout").cookie(refresh2)).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh").cookie(refresh2)).andExpect(status().isUnauthorized());
    }

    long createPerson(String bearer, String name, String gender) throws Exception {
        return body(mvc.perform(post("/api/v1/persons").header("Authorization", bearer).contentType(JSON)
            .content("{\"fullName\":\"" + name + "\",\"gender\":\"" + gender + "\"}")).andExpect(status().isCreated())).get("id").asLong();
    }
}
