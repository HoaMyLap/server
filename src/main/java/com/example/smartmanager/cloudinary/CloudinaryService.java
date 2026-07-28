package com.example.smartmanager.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryService {

    private final Cloudinary cloudinary;

    /**
     * Tải ảnh lên Cloudinary và trả về đường dẫn URL an toàn (HTTPS)
     */
    public String uploadImage(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File tải lên không được để trống");
        }

        try {
            Map uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", "smartmanager/uploads",
                    "resource_type", "image"
            ));

            String url = (String) uploadResult.get("secure_url");
            log.info("Tải ảnh lên Cloudinary thành công! URL: {}", url);
            return url;
        } catch (IOException e) {
            log.error("Lỗi tải ảnh lên Cloudinary: {}", e.getMessage());
            throw new RuntimeException("Tải ảnh lên Cloudinary thất bại: " + e.getMessage());
        }
    }

    /**
     * Tải tệp tin/tài liệu bất kỳ (PDF, Word, Excel, Zip, Image) lên Cloudinary
     */
    public String uploadFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File tải lên không được để trống");
        }

        try {
            Map uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", "homix/documents",
                    "resource_type", "auto"
            ));

            String url = (String) uploadResult.get("secure_url");
            log.info("Tải file lên Cloudinary thành công! URL: {}", url);
            return url;
        } catch (IOException e) {
            log.error("Lỗi tải file lên Cloudinary: {}", e.getMessage());
            throw new RuntimeException("Tải file lên Cloudinary thất bại: " + e.getMessage());
        }
    }
}
