package vn.giapha.person;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import vn.giapha.audit.AuditService;
import vn.giapha.audit.Snapshots;
import vn.giapha.auth.AuthUser;
import vn.giapha.common.ApiException;
import vn.giapha.storage.PhotoStorage;
import vn.giapha.storage.ThumbnailService;

@Service
public class PhotoService {
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png");
    static final long MAX_BYTES = 5L * 1024 * 1024;
    private final PersonRepository persons;
    private final EditPolicy policy;
    private final PhotoStorage storage;
    private final ThumbnailService thumbs;
    private final AuditService audit;

    public PhotoService(PersonRepository persons, EditPolicy policy, PhotoStorage storage, ThumbnailService thumbs, AuditService audit) {
        this.persons = persons;
        this.policy = policy;
        this.storage = storage;
        this.thumbs = thumbs;
        this.audit = audit;
    }

    static String key(long personId, String photoKey, String size) { return "p/" + personId + "/" + photoKey + "/" + size; }

    @Transactional
    public Person upload(AuthUser me, long personId, MultipartFile file) {
        Person p = persons.findByIdAndDeletedAtIsNull(personId).orElseThrow(this::notFound);
        policy.assertCanEdit(me, p);
        if (file.getContentType() == null || !TYPES.contains(file.getContentType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Chỉ nhận ảnh JPEG hoặc PNG");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "Tệp quá lớn (tối đa 5 MB)");
        }
        byte[] data;
        try { data = file.getBytes(); } catch (IOException e) { throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Không đọc được tệp"); }
        var t = thumbs.squares(data, 96, 256);      // ném 400 nếu không phải ảnh — trước khi ghi bất cứ thứ gì
        byte[] t96 = t.get(0);
        byte[] t256 = t.get(1);

        String oldKey = p.getPhotoKey();
        String newKey = UUID.randomUUID().toString();
        storage.put(key(personId, newKey, "orig"), data, file.getContentType());
        storage.put(key(personId, newKey, "96"), t96, "image/jpeg");
        storage.put(key(personId, newKey, "256"), t256, "image/jpeg");

        var before = Snapshots.of(p);
        p.setPhotoKey(newKey);
        p = persons.saveAndFlush(p);
        audit.record(me.id(), "PERSON", personId, "SET_PHOTO", before, Snapshots.of(p));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { if (oldKey != null) deleteAll(personId, oldKey); }
            @Override public void afterCompletion(int status) { if (status == STATUS_ROLLED_BACK) deleteAll(personId, newKey); }
        });
        return p;
    }

    private void deleteAll(long personId, String photoKey) {
        for (String s : new String[]{"orig", "96", "256"}) {
            try { storage.delete(key(personId, photoKey, s)); } catch (RuntimeException ignored) { /* dọn dẹp best-effort */ }
        }
    }

    public static String thumbUrl(long id, String photoKey) {
        return photoKey == null ? null : "/persons/" + id + "/photo?size=96&v=" + photoKey;
    }

    @Transactional(readOnly = true)
    public Optional<PhotoStorage.Stored> read(long personId, String size) {
        Person p = persons.findByIdAndDeletedAtIsNull(personId).orElseThrow(this::notFound);
        if (p.getPhotoKey() == null) return Optional.empty();
        String s = "96".equals(size) ? "96" : "orig".equals(size) ? "orig" : "256";
        return storage.get(key(personId, p.getPhotoKey(), s));
    }

    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy người này"); }
}
