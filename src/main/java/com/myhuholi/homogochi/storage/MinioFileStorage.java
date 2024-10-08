package com.myhuholi.homogochi.storage;


import com.myhuholi.homogochi.exception.StorageException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * Реализация интерфейса {@link FileStorage},
 * которая хранит файлы в удаленном хранилище Minio
 */
@RequiredArgsConstructor
public class MinioFileStorage implements FileStorage {
    /**
     * Клиент (соединение) удаленного хранилища
     */
    private final MinioClient minioClient;
    /**
     * Корневой путь хранилища
     */
    private final String bucket;

    /**
     * Инициализирует хранилище, создает bucket если он не существует
     */
    @PostConstruct
    public void init() {
        try {
            boolean bucketExists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(bucket)
                            .build()
            );
            if (!bucketExists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(bucket)
                                .build()
                );
            }
        } catch (Exception e) {
            throw new StorageException("Could not initialize storage", e);
        }
    }

    /**
     * Сохраняет файл в удаленном хранилище через {@link MinioClient} по указанному ключу
     * @param file Объект файла, содержащий данные в виде массива байтов и название файла, включая расширение
     * @param filePath Ключ для сохранения файла в хранилище
     * @throws StorageException если файл пустой, <br>
     * или если произошла иная ошибка
     */
    @Override
    public void store(MultipartFile file, String filePath) {
        if (file.isEmpty() ||  StringUtils.isBlank(file.getOriginalFilename())) {
            throw new StorageException("File is empty");
        }

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                            .stream(inputStream, inputStream.available(), -1)
                            .contentType(file.getContentType())
                            .bucket(bucket)
                            .object(filePath)
                            .build());
        } catch (Exception e) {
            throw new StorageException("Failed to store file: %s".formatted(file.getOriginalFilename()), e);
        }
    }

    /**
     * Удаляет файл по указанному ключу
     * @param filePath Ключ для нахождения файла
     * @throws StorageException если произошла ошибка
     */
    @Override
    public void delete(String filePath) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(filePath)
                            .build());
        } catch (Exception e) {
            throw new StorageException("Failed to delete file: %s".formatted(filePath), e);
        }
    }

    /**
     * Загружает файл по указанному ключу
     * @param filePath Ключ для нахождения файла
     * @return {@link InputStreamResource} реализацию интерфейса {@link Resource}
     * @throws StorageException если файл не существует<br>
     * или если файл невозможно прочитать<br>
     * или если произошла иная ошибка
     */
    @Override
    public Resource load(String filePath) {
        Resource resource;
        try {
            InputStream inputStream = minioClient.getObject(GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(filePath)
                            .build());
            resource = new InputStreamResource(inputStream);
        } catch (Exception e) {
            throw new StorageException("Failed to load file: %s".formatted(filePath), e);
        }

        if (resource.exists() || resource.isReadable()) {
            return resource;
        }

        throw new StorageException("File not found: %s".formatted(filePath), StorageError.NOT_FOUND_ERROR);
    }

    /**
     * Выгрузка файла в бинарном виде
     * @param filePath Путь/Ключ для нахождения файла
     * @return Файл в бинарном формате
     */
    @Override
    public byte[] loadFileBytes(String filePath) {
        Resource resource = load(filePath);
        try {
            return resource.getContentAsByteArray();
        } catch (IOException e) {
            throw new StorageException(String.format("Can't get file content as byte array: %s", filePath), e, StorageError.STORAGE_ERROR);
        }
    }

    @Override
    public void store(Resource file, String filePath) {
        if (!file.isFile() || !file.isReadable() || StringUtils.isBlank(file.getFilename())) {
            throw new StorageException("File is empty");
        }

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .stream(inputStream, inputStream.available(), -1)
                    .bucket(bucket)
                    .object(filePath)
                    .build());
        } catch (Exception e) {
            throw new StorageException("Failed to store file: %s".formatted(file.getFilename()), e);
        }
    }

    @Override
    public boolean isFileExists(String filePath) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(filePath).build());
            return true;
        } catch (ErrorResponseException e) {
            return false;
        } catch (Exception e) {
            throw new StorageException(e.getMessage(), e);
        }
    }
}
