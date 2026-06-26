package org.operaton.examples.contractsigning;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;
import jakarta.annotation.PostConstruct;

@Service
public class DocumentStore {
    private final S3Client s3Client;
    @Value("${storage.bucket:contracts}") private String defaultBucket;

    public DocumentStore(S3Client s3Client) { this.s3Client = s3Client; }

    @PostConstruct
    public void init() { createBucketIfNotExists(defaultBucket); }

    private void createBucketIfNotExists(String bucketName) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(req -> req.bucket(bucketName));
        }
    }

    public void put(String key, InputStream stream, long contentLength) {
        put(defaultBucket, key, stream, contentLength);
    }

    public void put(String bucketName, String key, InputStream stream, long contentLength) {
        PutObjectRequest putRequest = PutObjectRequest.builder()
            .bucket(bucketName).key(key).contentLength(contentLength).build();
        s3Client.putObject(putRequest, software.amazon.awssdk.core.sync.RequestBody.fromInputStream(stream, contentLength));
    }

    public InputStream get(String key) { return get(defaultBucket, key); }

    public InputStream get(String bucketName, String key) {
        GetObjectRequest getRequest = GetObjectRequest.builder().bucket(bucketName).key(key).build();
        ResponseInputStream<GetObjectResponse> response = s3Client.getObject(getRequest);
        return response;
    }
}
