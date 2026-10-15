package vn.giapha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import vn.giapha.auth.Role;
import vn.giapha.support.InMemoryPhotoStorage;
import vn.giapha.support.TestUsers;
import vn.giapha.support.TreeFixture;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestConfig.class, TestUsers.class, TreeFixture.class, InMemoryPhotoStorage.class})
@Transactional
class PermissionMatrixTest {
    /** body: nội dung JSON gửi kèm. Dòng nào có kiểm tra quyền ở service (sau khi validate body) phải gửi body HỢP LỆ,
     *  nếu không vai trò thấp sẽ nhận 400 thay vì 403. */
    record Ep(String method, String path, Role min, boolean multipart, String body) {}

    static Ep ep(String m, String p, Role r) { return new Ep(m, p, r, false, "{}"); }
    static final String VALID_PERSON = "{\"fullName\":\"Ma Trận\",\"gender\":\"M\",\"alive\":true,\"version\":0}";

    /** `{p}`=người, `{c}`=dòng họ, `{l}`=liên kết, `{u}`=hôn phối, `{a}`=mục audit, `{x}`=tài khoản. Mọi đường dẫn dưới /api/v1. */
    static final List<Ep> MATRIX = List.of(
        ep("GET", "/me", Role.VIEWER),
        ep("GET", "/clans", Role.VIEWER),
        ep("GET", "/clans/{c}/graph", Role.VIEWER),
        ep("GET", "/persons/{p}/focus", Role.VIEWER),
        ep("GET", "/persons/search?q=a", Role.VIEWER),
        ep("GET", "/persons/{p}", Role.VIEWER),
        ep("GET", "/persons/{p}/family", Role.VIEWER),
        ep("GET", "/persons/{p}/biography", Role.VIEWER),
        ep("GET", "/persons/{p}/photo", Role.VIEWER),
        ep("POST", "/persons", Role.EDITOR),
        new Ep("PUT", "/persons/{p}", Role.ADMIN, false, VALID_PERSON),   // EDITOR không phải người tạo → 403 từ EditPolicy
        new Ep("PUT", "/persons/{p}/photo", Role.ADMIN, true, null),
        ep("DELETE", "/persons/{p}", Role.ADMIN),
        ep("PUT", "/persons/{p}/birth-order", Role.EDITOR),
        ep("POST", "/relations/parent-child", Role.EDITOR),
        ep("POST", "/relations/children", Role.EDITOR),
        ep("DELETE", "/relations/parent-child/{l}", Role.EDITOR),
        ep("POST", "/relations/unions", Role.EDITOR),
        ep("PUT", "/relations/unions/{u}", Role.EDITOR),
        ep("DELETE", "/relations/unions/{u}", Role.EDITOR),
        ep("GET", "/audit?entityId={p}", Role.EDITOR),
        ep("GET", "/audit", Role.ADMIN),
        ep("POST", "/audit/{a}/revert", Role.ADMIN),
        ep("GET", "/admin/users", Role.ADMIN),
        ep("POST", "/admin/users", Role.ADMIN),
        ep("PUT", "/admin/users/{x}", Role.ADMIN),
        ep("GET", "/admin/clans", Role.ADMIN),
        ep("POST", "/admin/clans", Role.ADMIN),
        ep("PUT", "/admin/clans/{c}", Role.ADMIN),
        ep("GET", "/import/template", Role.ADMIN),
        new Ep("POST", "/import/excel", Role.ADMIN, true, null));

    /** Công khai: không cần đăng nhập. */
    static final Set<String> PUBLIC = Set.of("POST /auth/register", "POST /auth/login", "POST /auth/refresh", "POST /auth/logout");

    @Autowired MockMvc mvc;
    @Autowired TestUsers users;
    @Autowired TreeFixture fixture;
    @Autowired ObjectMapper om;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;

    static final Pattern VAR = Pattern.compile("\\{[^}]+}");
    static String norm(String s) { return VAR.matcher(s).replaceAll("{}"); }

    MockHttpServletRequestBuilder build(Ep e, TreeFixture.Ids ids, String bearer) {
        String path = "/api/v1" + e.path().replace("{p}", String.valueOf(ids.a1())).replace("{c}", String.valueOf(ids.clan()))
            .replace("{l}", "1").replace("{u}", "1").replace("{a}", "1").replace("{x}", "1");
        MockHttpServletRequestBuilder b;
        if (e.multipart()) {
            b = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(HttpMethod.valueOf(e.method()), path)
                .file(new MockMultipartFile("file", "x.png", "image/png", new byte[]{1, 2, 3}));
        } else {
            b = request(HttpMethod.valueOf(e.method()), path).contentType(MediaType.APPLICATION_JSON).content(e.body());
        }
        return bearer == null ? b : b.header("Authorization", bearer);
    }

    @Test
    void everyEndpointEnforcesItsMinimumRole() throws Exception {
        var ids = fixture.create();
        List<String> failures = new ArrayList<>();
        for (Ep e : MATRIX) {
            int anon = mvc.perform(build(e, ids, null)).andReturn().getResponse().getStatus();
            if (anon != 401) failures.add(e.method() + " " + e.path() + " ẩn danh -> " + anon + " (cần 401)");
            for (Role r : Role.values()) {
                int s = mvc.perform(build(e, ids, users.bearer(r))).andReturn().getResponse().getStatus();
                boolean allowed = r.ordinal() <= e.min().ordinal();   // ADMIN(0) > EDITOR(1) > VIEWER(2)
                if (allowed && (s == 401 || s == 403)) failures.add(e.method() + " " + e.path() + " với " + r + " -> " + s + " (phải được phép)");
                if (!allowed && s != 403) failures.add(e.method() + " " + e.path() + " với " + r + " -> " + s + " (cần 403)");
            }
        }
        assertThat(failures).isEmpty();
    }

    @Test
    void publicEndpointsDoNotRequireLogin() throws Exception {
        for (String p : PUBLIC) {
            String[] parts = p.split(" ");
            var res = mvc.perform(request(HttpMethod.valueOf(parts[0]), "/api/v1" + parts[1]).contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse();
            // refresh/login có thể trả 401 nghiệp vụ (không có cookie/sai mật khẩu); chỉ cấm 403 và 401 do bộ lọc bảo mật ("Cần đăng nhập").
            assertThat(res.getStatus()).as(p).isNotEqualTo(403);
            assertThat(res.getContentAsString()).as(p).doesNotContain("Cần đăng nhập");
        }
    }

    @Test
    void guardEveryDeclaredEndpointIsInTheMatrixAndInOpenApi() throws Exception {
        Set<String> declared = new TreeSet<>();
        for (Map.Entry<RequestMappingInfo, org.springframework.web.method.HandlerMethod> en : mappings.getHandlerMethods().entrySet()) {
            if (!en.getValue().getBeanType().getPackageName().startsWith("vn.giapha")) continue;
            var patterns = en.getKey().getPathPatternsCondition().getPatternValues();
            for (String pattern : patterns) {
                for (var m : en.getKey().getMethodsCondition().getMethods()) declared.add(m.name() + " " + norm(pattern.replaceFirst("^/api/v1", "")));
            }
        }
        Set<String> covered = MATRIX.stream().map(e -> e.method() + " " + norm(e.path().replaceFirst("\\?.*$", ""))).collect(Collectors.toCollection(TreeSet::new));
        PUBLIC.forEach(p -> covered.add(p.split(" ")[0] + " " + norm(p.split(" ")[1])));
        Set<String> uncovered = new TreeSet<>(declared);
        uncovered.removeAll(covered);
        assertThat(uncovered).as("Endpoint chưa có trong ma trận quyền").isEmpty();

        JsonNode paths = om.readTree(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v3/api-docs"))
            .andReturn().getResponse().getContentAsString()).get("paths");
        Set<String> documented = new TreeSet<>();
        paths.fieldNames().forEachRemaining(p -> paths.get(p).fieldNames().forEachRemaining(m -> documented.add(m.toUpperCase() + " " + norm(p.replaceFirst("^/api/v1", "")))));
        Set<String> undocumented = new TreeSet<>(declared);
        undocumented.removeAll(documented);
        assertThat(undocumented).as("Endpoint chưa có trong OpenAPI").isEmpty();
    }
}
