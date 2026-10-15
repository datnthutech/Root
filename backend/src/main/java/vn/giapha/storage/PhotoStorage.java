package vn.giapha.storage;

import java.util.Optional;

public interface PhotoStorage {
    record Stored(byte[] data, String contentType) {}

    void put(String key, byte[] data, String contentType);
    Optional<Stored> get(String key);
    void delete(String key);
}
