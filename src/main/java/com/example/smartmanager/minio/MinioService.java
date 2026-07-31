package com.example.smartmanager.minio;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioService {

    private final MinioClient minioClient;

    @Value("${minio.bucket-name:smartmanager}")
    private String bucketName;

    @Value("${minio.public-endpoint:http://localhost:9000}")
    private String publicEndpoint;

    @PostConstruct
    public void initBucket() {
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("Đã tạo mới MinIO bucket: {}", bucketName);
            }

            // Cấu hình Policy cho phép truy cập đọc public (GetObject) cho mọi file trong bucket
            String policyJson = """
                {
                  "Version": "2012-10-17",
                  "Statement": [
                    {
                      "Effect": "Allow",
                      "Principal": {"AWS": ["*"]},
                      "Action": ["s3:GetObject"],
                      "Resource": ["arn:aws:s3:::%s/*"]
                    }
                  ]
                }
                """.formatted(bucketName);

            minioClient.setBucketPolicy(
                    SetBucketPolicyArgs.builder()
                            .bucket(bucketName)
                            .config(policyJson)
                            .build()
            );
            log.info("Đã thiết lập Public Read policy cho MinIO bucket: {}", bucketName);
        } catch (Exception e) {
            log.error("Lỗi khi khởi tạo MinIO bucket: {}", e.getMessage(), e);
        }
    }

    /**
     * Tải ảnh lên MinIO và trả về URL xem ảnh trực tiếp
     */
    public String uploadImage(MultipartFile file) {
        return upload(file, "images/");
    }

    /**
     * Tải tài liệu/file bất kỳ lên MinIO và trả về URL tải về
     */
    public String uploadFile(MultipartFile file) {
        return upload(file, "documents/");
    }

    private String upload(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File tải lên không được để trống");
        }

        try {
            String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
            String cleanFilename = originalFilename.replaceAll("\\s+", "_");
            String objectName = folder + UUID.randomUUID() + "_" + cleanFilename;

            try (InputStream is = file.getInputStream()) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucketName)
                                .object(objectName)
                                .stream(is, file.getSize(), -1)
                                .contentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                                .build()
                );
            }

            String fileUrl = publicEndpoint + "/" + bucketName + "/" + objectName;
            log.info("Tải file lên MinIO thành công! URL: {}", fileUrl);
            return fileUrl;
        } catch (Exception e) {
            log.error("Lỗi tải file lên MinIO: {}", e.getMessage(), e);
            throw new RuntimeException("Tải file lên MinIO thất bại: " + e.getMessage());
        }
    }
}
