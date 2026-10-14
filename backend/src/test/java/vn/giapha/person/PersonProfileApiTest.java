package vn.giapha.person;

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
class PersonProfileApiTest {
    @Autowired MockMvc mvc;
    @Autowired TestUsers users;
    @Autowired TreeFixture fixture;
    @Autowired PersonRepository persons;

    @Test
    void viewerDoesNotSeeFullBirthDateOfLivingPerson() throws Exception {
        var ids = fixture.create();
        String body = mvc.perform(get("/api/v1/persons/" + ids.a2()).header("Authorization", users.bearer(Role.VIEWER)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.birthYear").value(1953))
            .andExpect(jsonPath("$.birthDate").doesNotExist()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("1953-04-05");
    }

    @Test
    void editorAndAdminSeeFullBirthDate() throws Exception {
        var ids = fixture.create();
        for (Role r : new Role[]{Role.EDITOR, Role.ADMIN}) {
            mvc.perform(get("/api/v1/persons/" + ids.a2()).header("Authorization", users.bearer(r)))
               .andExpect(jsonPath("$.birthDate").value("1953-04-05"));
        }
    }

    @Test
    void viewerSeesFullDatesOfDeceasedPerson() throws Exception {
        var ids = fixture.create();
        Person g = persons.findById(ids.g()).orElseThrow();
        g.setBirthDate(java.time.LocalDate.of(1900, 2, 3)); g.setBirthPrecision(DatePrecision.DAY);
        persons.saveAndFlush(g);
        mvc.perform(get("/api/v1/persons/" + ids.g()).header("Authorization", users.bearer(Role.VIEWER)))
           .andExpect(jsonPath("$.birthDate").value("1900-02-03"))
           .andExpect(jsonPath("$.generation").value(1));
    }

    @Test
    void profileContainsOrderLabelAndGeneration() throws Exception {
        var ids = fixture.create();
        mvc.perform(get("/api/v1/persons/" + ids.a()).header("Authorization", users.bearer(Role.VIEWER)))
           .andExpect(jsonPath("$.orderLabel").value("anh Hai")).andExpect(jsonPath("$.generation").value(2));
    }

    @Test
    void biographyAndErrors() throws Exception {
        var ids = fixture.create();
        Person g = persons.findById(ids.g()).orElseThrow();
        g.setBiography("Thủy tổ họ Nguyễn.\nSinh năm 1900.");
        persons.saveAndFlush(g);
        mvc.perform(get("/api/v1/persons/" + ids.g() + "/biography").header("Authorization", users.bearer(Role.VIEWER)))
           .andExpect(jsonPath("$.biography").value("Thủy tổ họ Nguyễn.\nSinh năm 1900."));
        mvc.perform(get("/api/v1/persons/99999999").header("Authorization", users.bearer(Role.VIEWER))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/persons/" + ids.g())).andExpect(status().isUnauthorized());
    }

    @Test
    void softDeletedPersonIs404() throws Exception {
        var ids = fixture.create();
        Person c = persons.findById(ids.c()).orElseThrow();
        c.setDeletedAt(java.time.Instant.now());
        persons.saveAndFlush(c);
        mvc.perform(get("/api/v1/persons/" + ids.c()).header("Authorization", users.bearer(Role.ADMIN))).andExpect(status().isNotFound());
    }
}
