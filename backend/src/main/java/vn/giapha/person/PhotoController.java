package vn.giapha.person;

import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.giapha.auth.AuthUser;
import vn.giapha.common.ApiException;

@RestController
@RequestMapping("/api/v1/persons")
public class PhotoController {
    public record UploadResponse(long personId, String photoKey, String thumb) {}

    private final PhotoService photos;

    public PhotoController(PhotoService photos) { this.photos = photos; }

    @PutMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('EDITOR')")
    public UploadResponse upload(@AuthenticationPrincipal AuthUser me, @PathVariable long id, @RequestParam("file") MultipartFile file) {
        Person p = photos.upload(me, id, file);
        return new UploadResponse(id, p.getPhotoKey(), "/persons/" + id + "/photo?size=96&v=" + p.getPhotoKey());
    }

    @GetMapping("/{id}/photo")
    @PreAuthorize("hasRole('VIEWER')")
    public ResponseEntity<byte[]> download(@PathVariable long id, @RequestParam(defaultValue = "256") String size) {
        var stored = photos.read(id, size).orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "NOT_FOUND", "Chưa có ảnh"));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(stored.contentType()))
            .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePrivate().immutable()).body(stored.data());
    }
}
