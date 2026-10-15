package vn.giapha.support;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Primary;
import vn.giapha.storage.PhotoStorage;

@TestComponent
@Primary
public class InMemoryPhotoStorage implements PhotoStorage {
    public final Map<String, Stored> data = new ConcurrentHashMap<>();

    @Override public void put(String key, byte[] bytes, String contentType) { data.put(key, new Stored(bytes, contentType)); }
    @Override public Optional<Stored> get(String key) { return Optional.ofNullable(data.get(key)); }
    @Override public void delete(String key) { data.remove(key); }
}
