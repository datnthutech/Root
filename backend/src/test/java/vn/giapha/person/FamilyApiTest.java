package vn.giapha.person;

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
class FamilyApiTest {
    @Autowired MockMvc mvc;
    @Autowired TestUsers users;
    @Autowired TreeFixture fixture;

    String url(long id) { return "/api/v1/persons/" + id + "/family"; }

    @Test
    void fatherAHasTwoSpouseFamiliesWithTheirChildren() throws Exception {
        var ids = fixture.create();
        mvc.perform(get(url(ids.a())).header("Authorization", users.bearer(Role.VIEWER)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.parents.length()").value(2))
           .andExpect(jsonPath("$.spouses.length()").value(2))
           .andExpect(jsonPath("$.spouses[0].unionOrder").value(1))
           .andExpect(jsonPath("$.spouses[0].partner.id").value(ids.aw()))
           .andExpect(jsonPath("$.spouses[0].children.length()").value(2))
           .andExpect(jsonPath("$.spouses[1].partner.id").value(ids.aw2()))
           .andExpect(jsonPath("$.spouses[1].children[0].id").value(ids.a3()))
           .andExpect(jsonPath("$.fullSiblings.length()").value(2));   // B và C
    }

    @Test
    void a1HasFullSiblingA2AndHalfSiblingA3SameFather() throws Exception {
        var ids = fixture.create();
        mvc.perform(get(url(ids.a1())).header("Authorization", users.bearer(Role.VIEWER)))
           .andExpect(jsonPath("$.fullSiblings.length()").value(1))
           .andExpect(jsonPath("$.fullSiblings[0].id").value(ids.a2()))
           .andExpect(jsonPath("$.halfSiblingsSameFather.length()").value(1))
           .andExpect(jsonPath("$.halfSiblingsSameFather[0].id").value(ids.a3()))
           .andExpect(jsonPath("$.halfSiblingsSameMother.length()").value(0));
    }

    @Test
    void personRefCarriesSouthernOrderLabel() throws Exception {
        var ids = fixture.create();
        mvc.perform(get(url(ids.a1())).header("Authorization", users.bearer(Role.VIEWER)))
           .andExpect(jsonPath("$.fullSiblings[0].orderLabel").value("chị Út (thứ Ba)"));
    }

    @Test
    void rootPersonHasNoParentsOrSiblings() throws Exception {
        var ids = fixture.create();
        mvc.perform(get(url(ids.g())).header("Authorization", users.bearer(Role.VIEWER)))
           .andExpect(jsonPath("$.parents.length()").value(0))
           .andExpect(jsonPath("$.fullSiblings.length()").value(0))
           .andExpect(jsonPath("$.halfSiblingsSameFather.length()").value(0))
           .andExpect(jsonPath("$.spouses.length()").value(1));
    }

    @Test
    void errors() throws Exception {
        mvc.perform(get(url(99999999)).header("Authorization", users.bearer(Role.VIEWER))).andExpect(status().isNotFound());
        mvc.perform(get(url(1))).andExpect(status().isUnauthorized());
    }
}
