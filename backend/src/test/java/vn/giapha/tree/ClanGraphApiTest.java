package vn.giapha.tree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.PostgresTestConfig;
import vn.giapha.auth.Role;
import vn.giapha.support.TestUsers;
import vn.giapha.support.TreeFixture;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestConfig.class, TestUsers.class, TreeFixture.class})
@Transactional
class ClanGraphApiTest {
    @Autowired MockMvc mvc;
    @Autowired TestUsers users;
    @Autowired TreeFixture fixture;

    @Test
    void requiresLogin() throws Exception {
        var ids = fixture.create();
        mvc.perform(get("/api/v1/clans")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/clans/" + ids.clan() + "/graph")).andExpect(status().isUnauthorized());
    }

    @Test
    void listsClansWithMemberCount() throws Exception {
        var ids = fixture.create();
        mvc.perform(get("/api/v1/clans").header("Authorization", users.bearer(Role.VIEWER)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[?(@.id==" + ids.clan() + ")].memberCount").value(12));
    }

    @Test
    void graphHasShapeOrdersAndNoFullDates() throws Exception {
        var ids = fixture.create();
        String body = mvc.perform(get("/api/v1/clans/" + ids.clan() + "/graph").header("Authorization", users.bearer(Role.VIEWER)))
            .andExpect(status().isOk())
            .andExpect(header().exists("ETag"))
            .andExpect(jsonPath("$.rootPersonId").value(ids.g()))
            .andExpect(jsonPath("$.persons.length()").value(12))
            .andExpect(jsonPath("$.unions.length()").value(4))
            .andExpect(jsonPath("$.links.length()").value(14))
            .andExpect(jsonPath("$.persons[?(@.id==" + ids.a() + ")].orderLabel").value("anh Hai"))
            .andExpect(jsonPath("$.persons[?(@.id==" + ids.b() + ")].orderLabel").value("chị Ba"))
            .andExpect(jsonPath("$.persons[?(@.id==" + ids.c() + ")].orderLabel").value("anh Út (thứ Tư)"))
            .andExpect(jsonPath("$.persons[?(@.id==" + ids.a2() + ")].birthYear").value(1953))
            .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("1953-04-05").doesNotContain("birthDate");
    }

    @Test
    void halfSiblingsAreOrderedPerUnion() throws Exception {
        var ids = fixture.create();
        // A3 là con duy nhất của hôn phối thứ 2 nên là thứ Hai trong nhóm của nó, chưa phải "Út" vì nhóm chỉ có 1 người
        mvc.perform(get("/api/v1/clans/" + ids.clan() + "/graph").header("Authorization", users.bearer(Role.VIEWER)))
           .andExpect(jsonPath("$.persons[?(@.id==" + ids.a3() + ")].orderLabel").value("anh Hai"));
    }

    @Test
    void etagGives304WhenUnchanged() throws Exception {
        var ids = fixture.create();
        String auth = users.bearer(Role.VIEWER);
        String etag = mvc.perform(get("/api/v1/clans/" + ids.clan() + "/graph").header("Authorization", auth))
            .andReturn().getResponse().getHeader("ETag");
        mvc.perform(get("/api/v1/clans/" + ids.clan() + "/graph").header("Authorization", auth).header("If-None-Match", etag))
           .andExpect(status().isNotModified());
    }

    @Test
    void paternalSideDropsDaughtersChildren() throws Exception {
        var ids = fixture.create();
        String auth = users.bearer(Role.VIEWER);
        String body = mvc.perform(get("/api/v1/clans/" + ids.clan() + "/graph?side=paternal").header("Authorization", auth))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("Đặng Văn Bảo");      // B1: con của con gái B
        assertThat(body).contains("Nguyễn Văn Một");          // A1
        assertThat(body).contains("Đặng Văn Hùng");           // chồng của B (B vẫn được giữ)
        // không có cạnh nào trỏ tới người vắng mặt
        var tree = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
        var present = new java.util.HashSet<Long>();
        tree.get("persons").forEach(p -> present.add(p.get("id").asLong()));
        tree.get("links").forEach(l -> { assertThat(present).contains(l.get("parentId").asLong(), l.get("childId").asLong()); });
        tree.get("unions").forEach(u -> { assertThat(present).contains(u.get("partnerAId").asLong(), u.get("partnerBId").asLong()); });
    }

    @Test
    void badSideIs400AndUnknownClanIs404() throws Exception {
        var ids = fixture.create();
        String auth = users.bearer(Role.VIEWER);
        mvc.perform(get("/api/v1/clans/" + ids.clan() + "/graph?side=abc").header("Authorization", auth)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/clans/99999999/graph").header("Authorization", auth)).andExpect(status().isNotFound());
    }

    @Test
    void clanWithoutFounderGivesEmptyGraph() throws Exception {
        var clan = new vn.giapha.clan.Clan();
        clan.setName("Rỗng");
        clan = clanRepo.save(clan);
        mvc.perform(get("/api/v1/clans/" + clan.getId() + "/graph").header("Authorization", users.bearer(Role.VIEWER)))
           .andExpect(status().isOk()).andExpect(jsonPath("$.persons.length()").value(0));
    }

    @Autowired vn.giapha.clan.ClanRepository clanRepo;
}
