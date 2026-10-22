package vn.giapha.storage;

import java.net.URI;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;

@Component
public class S3PhotoStorage implements PhotoStorage {
    private final S3Client s3;
    private final String bucket;
    private volatile boolean bucketReady;

    public S3PhotoStorage(@Value("${app.storage.endpoint}") String endpoint, @Value("${app.storage.access-key}") String accessKey,
                          @Value("${app.storage.secret-key}") String secretKey, @Value("${app.storage.bucket}") String bucket) {
        this.bucket = bucket;
        this.s3 = S3Client.builder().endpointOverride(URI.create(endpoint)).region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build()).build();
    }

    private void ensureBucket() {
        if (bucketReady) return;
        try { s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build()); }
        catch (S3Exception e) {
            if (e.statusCode() != 404) throw e;
            try { s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build()); }
            catch (BucketAlreadyOwnedByYouException | BucketAlreadyExistsException ignored) { /* created concurrently */ }
        }
        bucketReady = true;
    }

    @Override
    public void put(String key, byte[] data, String contentType) {
        ensureBucket();
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(), RequestBody.fromBytes(data));
    }

    @Override
    public Optional<Stored> get(String key) {
        try {
            var r = s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build());
            return Optional.of(new Stored(r.asByteArray(), r.response().contentType()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }
}
