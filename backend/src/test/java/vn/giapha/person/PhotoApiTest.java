package vn.giapha.person;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.PostgresTestConfig;
import vn.giapha.auth.*;
import vn.giapha.support.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestConfig.class, TestUsers.class, TreeFixture.class, InMemoryPhotoStorage.class})
@Transactional
class PhotoApiTest {
    @Autowired MockMvc mvc;
    @Autowired TestUsers users;
    @Autowired TreeFixture fixture;
    @Autowired InMemoryPhotoStorage storage;
    @Autowired PersonRepository persons;

    @org.junit.jupiter.api.BeforeEach
    void clearStorage() { storage.data.clear(); }  // bean dùng chung, không rollback theo @Transactional

    static byte[] png() throws Exception {
        BufferedImage img = new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB);
        var out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    MockMultipartFile file(byte[] data, String type) { return new MockMultipartFile("file", "a.png", type, data); }

    @Test
    void adminUploadsThenAnyUserDownloadsResizedThumbs() throws Exception {
        var ids = fixture.create();
        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/persons/{id}/photo", ids.a()).file(file(png(), "image/png"))
            .header("Authorization", users.bearer(Role.ADMIN)))
           .andExpect(status().isOk()).andExpect(jsonPath("$.photoKey").isNotEmpty());
        byte[] t96 = mvc.perform(get("/api/v1/persons/" + ids.a() + "/photo?size=96&v=x").header("Authorization", users.bearer(Role.VIEWER)))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")))
            .andReturn().getResponse().getContentAsByteArray();
        assertThat(ImageIO.read(new ByteArrayInputStream(t96)).getWidth()).isEqualTo(96);
        byte[] orig = mvc.perform(get("/api/v1/persons/" + ids.a() + "/photo?size=orig").header("Authorization", users.bearer(Role.VIEWER)))
            .andReturn().getResponse().getContentAsByteArray();
        assertThat(ImageIO.read(new ByteArrayInputStream(orig)).getWidth()).isEqualTo(300);
        assertThat(storage.data).hasSize(3);
    }

    @Test
    void graphThumbPointsToUploadedPhoto() throws Exception {
        var ids = fixture.create();
        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/persons/{id}/photo", ids.a()).file(file(png(), "image/png"))
            .header("Authorization", users.bearer(Role.ADMIN))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/clans/" + ids.clan() + "/graph").header("Authorization", users.bearer(Role.VIEWER)))
           .andExpect(jsonPath("$.persons[?(@.id==" + ids.a() + ")].thumb").value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.startsWith("/persons/" + ids.a() + "/photo?size=96&v="))));
    }

    private void fireSyncs(java.util.function.Consumer<org.springframework.transaction.support.TransactionSynchronization> f) {
        new java.util.ArrayList<>(org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()).forEach(f);
    }

    @Test
    void replacingPhotoDeletesOldObjectsAfterCommit() throws Exception {
        var ids = fixture.create();
        String admin = users.bearer(Role.ADMIN);
        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/persons/{id}/photo", ids.a()).file(file(png(), "image/png")).header("Authorization", admin)).andExpect(status().isOk());
        var firstKeys = new java.util.HashSet<>(storage.data.keySet());
        assertThat(firstKeys).hasSize(3);
        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/persons/{id}/photo", ids.a()).file(file(png(), "image/png")).header("Authorization", admin)).andExpect(status().isOk());
        // trước commit: chưa xoá gì, cả hai bộ cùng tồn tại
        assertThat(storage.data).hasSize(6);
        // tests chạy trong giao dịch không commit, nên tự gọi callback afterCommit như Spring sẽ làm
        fireSyncs(sync -> sync.afterCommit());
        assertThat(storage.data).hasSize(3).doesNotContainKeys(firstKeys.toArray(new String[0]));
    }

    @Test
    void rollbackDeletesNewlyUploadedObjects() throws Exception {
        var ids = fixture.create();
        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/persons/{id}/photo", ids.a()).file(file(png(), "image/png"))
            .header("Authorization", users.bearer(Role.ADMIN))).andExpect(status().isOk());
        assertThat(storage.data).hasSize(3);
        fireSyncs(sync -> sync.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK));
        assertThat(storage.data).isEmpty();
    }

    @Test
    void oversizeUploadIsRejectedWith413() throws Exception {
        var ids = fixture.create();
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/persons/{id}/photo", ids.a()).file(file(big, "image/png"))
            .header("Authorization", users.bearer(Role.ADMIN)))
           .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
        assertThat(storage.data).isEmpty();
    }

    @Test
    void cacheControlIsImmutableOnlyWithVersionParam() throws Exception {
        var ids = fixture.create();
        String admin = users.bearer(Role.ADMIN);
        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/persons/{id}/photo", ids.a()).file(file(png(), "image/png")).header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/persons/" + ids.a() + "/photo?size=96&v=abc").header("Authorization", admin))
           .andExpect(header().string("Cache-Control", "max-age=31536000, private, immutable"));
        mvc.perform(get("/api/v1/persons/" + ids.a() + "/photo?size=96").header("Authorization", admin))
           .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.allOf(
               org.hamcrest.Matchers.containsString("no-cache"), org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("immutable")))));
    }

    @Test
    void permissionMatrix() throws Exception {
        var ids = fixture.create();
        var f = file(png(), "image/png");
        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/persons/{id}/photo", ids.a()).file(f)).andExpect(status().isUnauthorized());
        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/persons/{id}/photo", ids.a()).file(f).header("Authorization", users.bearer(Role.VIEWER))).andExpect(status().isForbidden());
        // EDITOR không phải người tạo (người trong fixture không có created_by)
        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/persons/{id}/photo", ids.a()).file(f).header("Authorization", users.bearer(Role.EDITOR))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/persons/" + ids.a() + "/photo")).andExpect(status().isUnauthorized());
    }

    @Test
    void editorUploadsForPersonTheyCreated() throws Exception {
        var ids = fixture.create();
        AppUser editor = users.create(Role.EDITOR);
        Person p = persons.findById(ids.c()).orElseThrow();
        p.setCreatedBy(editor.getId());
        persons.saveAndFlush(p);
        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/persons/{id}/photo", ids.c()).file(file(png(), "image/png")).header("Authorization", users.bearerFor(editor)))
           .andExpect(status().isOk());
    }

    @Test
    void rejectsFakeImagesWrongTypesAndMissingPhoto() throws Exception {
        var ids = fixture.create();
        String admin = users.bearer(Role.ADMIN);
        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/persons/{id}/photo", ids.a()).file(file("hello".getBytes(), "image/jpeg")).header("Authorization", admin))
           .andExpect(status().isBadRequest());
        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/persons/{id}/photo", ids.a()).file(file(png(), "text/plain")).header("Authorization", admin))
           .andExpect(status().isBadRequest());
        assertThat(persons.findById(ids.a()).orElseThrow().getPhotoKey()).isNull();
        mvc.perform(get("/api/v1/persons/" + ids.a() + "/photo").header("Authorization", admin)).andExpect(status().isNotFound());
    }
}
