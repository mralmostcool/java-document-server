package pdfserver.storage;

import org.springframework.stereotype.Service;
import pdfserver.config.S3Properties;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;

@Service
public class S3ObjectStorage implements ObjectStorage {

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    public S3ObjectStorage(S3Client s3Client, S3Properties s3Properties) {
        this.s3Client = s3Client;
        this.s3Properties = s3Properties;
    }

    private String buildKey(String ownerId, String documentId) {
        return ownerId + "/" + documentId;
    }

    @Override
    public void put(String ownerId, String documentId, InputStream data, long size, String contentType) {
        String key = buildKey(ownerId, documentId);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(s3Properties.bucket())
                .key(key)
                .contentType(contentType)
                .contentLength(size)
                .build();

        s3Client.putObject(request, RequestBody.fromInputStream(data, size));
    }

    @Override
    public InputStream get(String ownerId, String documentId) {
        String key = buildKey(ownerId, documentId);
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(s3Properties.bucket())
                .key(key)
                .build();

        return s3Client.getObject(request);
    }

    @Override
    public boolean exists(String ownerId, String documentId) {
        String key = buildKey(ownerId, documentId);
        HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(s3Properties.bucket())
                .key(key)
                .build();

        try {
            s3Client.headObject(request);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            // S3 headObject returns 404 when key not found, AWS SDK may throw NoSuchKeyException or S3Exception with 404
            if (e instanceof software.amazon.awssdk.services.s3.model.S3Exception s3Ex && s3Ex.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public void delete(String ownerId, String documentId) {
        String key = buildKey(ownerId, documentId);
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(s3Properties.bucket())
                .key(key)
                .build();

        s3Client.deleteObject(request);
    }
}

