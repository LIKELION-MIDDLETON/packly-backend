package com.jaungangton.api.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.ChecksumAlgorithm;
import software.amazon.awssdk.services.s3.model.ChecksumMode;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

class S3PhotoStorageAdapterTest {
    @Test
    void uploadsPrivateEncryptedObjectUnderConfiguredAnalysisPrefix() {
        S3Client s3 = mock(S3Client.class);
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        S3PhotoStorageAdapter storage = new S3PhotoStorageAdapter(s3, "packly-photos", "/prod/photos/");
        UUID analysisId = UUID.randomUUID();

        PhotoStorageReference reference = storage.store(analysisId, new byte[] {1, 2, 3}, "image/png");

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("packly-photos");
        assertThat(request.getValue().key()).startsWith("prod/photos/" + analysisId + "/");
        assertThat(request.getValue().key()).isEqualTo(reference.objectKey());
        assertThat(request.getValue().serverSideEncryption()).isEqualTo(ServerSideEncryption.AES256);
        assertThat(request.getValue().acl()).isNull();
        assertThat(request.getValue().contentType()).isEqualTo("image/png");
        assertThat(request.getValue().checksumAlgorithm()).isEqualTo(ChecksumAlgorithm.SHA256);
        assertThat(request.getValue().checksumSHA256())
                .isEqualTo(Base64.getEncoder().encodeToString(HexFormat.of().parseHex(reference.checksum())));
        assertThat(request.getValue().metadata()).containsEntry("sha256", reference.checksum());
        assertThat(reference.databaseData()).isNull();
    }

    @Test
    void downloadsAndVerifiesStoredObject() {
        S3Client s3 = mock(S3Client.class);
        byte[] data = {4, 5, 6};
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        when(s3.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength((long) data.length).build());
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), data));
        S3PhotoStorageAdapter storage = new S3PhotoStorageAdapter(s3, "packly-photos", "analysis-photos");
        PhotoStorageReference reference = storage.store(UUID.randomUUID(), data, "image/webp");

        assertThat(storage.load(reference)).containsExactly(data);
        ArgumentCaptor<HeadObjectRequest> headRequest = ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(s3).headObject(headRequest.capture());
        assertThat(headRequest.getValue().key()).isEqualTo(reference.objectKey());
        assertThat(headRequest.getValue().checksumMode()).isEqualTo(ChecksumMode.ENABLED);
        ArgumentCaptor<GetObjectRequest> getRequest = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3).getObjectAsBytes(getRequest.capture());
        assertThat(getRequest.getValue().checksumMode()).isEqualTo(ChecksumMode.ENABLED);
    }

    @Test
    void rejectsDownloadedObjectWhenIntegrityMetadataDoesNotMatch() {
        S3Client s3 = mock(S3Client.class);
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        when(s3.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(1L).build());
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[] {9}));
        S3PhotoStorageAdapter storage = new S3PhotoStorageAdapter(s3, "packly-photos", "analysis-photos");
        PhotoStorageReference reference = storage.store(UUID.randomUUID(), new byte[] {1}, "image/jpeg");

        assertThatThrownBy(() -> storage.load(reference))
                .isInstanceOf(PhotoStorageException.class)
                .hasMessage("Stored photo integrity check failed");
    }

    @Test
    void rejectsObjectLargerThanStoredReferenceBeforeDownloadingIt() {
        S3Client s3 = mock(S3Client.class);
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        when(s3.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(2L).build());
        S3PhotoStorageAdapter storage = new S3PhotoStorageAdapter(s3, "packly-photos", "analysis-photos");
        PhotoStorageReference reference = storage.store(UUID.randomUUID(), new byte[] {1}, "image/jpeg");

        assertThatThrownBy(() -> storage.load(reference))
                .isInstanceOf(PhotoStorageException.class)
                .hasMessage("Stored photo integrity check failed");
        verify(s3, never()).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    void rejectsNativeChecksumMismatchBeforeDownloadingIt() {
        S3Client s3 = mock(S3Client.class);
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        when(s3.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder()
                        .contentLength(1L)
                        .checksumSHA256("not-the-expected-checksum")
                        .build());
        S3PhotoStorageAdapter storage = new S3PhotoStorageAdapter(s3, "packly-photos", "analysis-photos");
        PhotoStorageReference reference = storage.store(UUID.randomUUID(), new byte[] {1}, "image/jpeg");

        assertThatThrownBy(() -> storage.load(reference))
                .isInstanceOf(PhotoStorageException.class)
                .hasMessage("Stored photo integrity check failed");
        verify(s3, never()).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    void deletesStoredObject() {
        S3Client s3 = mock(S3Client.class);
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        when(s3.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());
        S3PhotoStorageAdapter storage = new S3PhotoStorageAdapter(s3, "packly-photos", "analysis-photos");
        PhotoStorageReference reference = storage.store(UUID.randomUUID(), new byte[] {1}, "image/jpeg");

        storage.delete(reference);

        ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3).deleteObject(request.capture());
        assertThat(request.getValue().key()).isEqualTo(reference.objectKey());
    }

    @Test
    void attemptsBestEffortCleanupWhenUploadFails() {
        S3Client s3 = mock(S3Client.class);
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().statusCode(500).message("unavailable").build());
        when(s3.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());
        S3PhotoStorageAdapter storage = new S3PhotoStorageAdapter(s3, "packly-photos", "analysis-photos");

        assertThatThrownBy(() -> storage.store(UUID.randomUUID(), new byte[] {1}, "image/jpeg"))
                .isInstanceOf(PhotoStorageException.class)
                .hasMessage("Could not store photo in S3");
        verify(s3).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void wrapsDownloadAndDeleteProviderErrors() {
        S3Client s3 = mock(S3Client.class);
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        when(s3.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(1L).build());
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(503).message("download failed").build());
        when(s3.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(503).message("delete failed").build());
        S3PhotoStorageAdapter storage = new S3PhotoStorageAdapter(s3, "packly-photos", "analysis-photos");
        PhotoStorageReference reference = storage.store(UUID.randomUUID(), new byte[] {1}, "image/jpeg");

        assertThatThrownBy(() -> storage.load(reference))
                .isInstanceOf(PhotoStorageException.class)
                .hasMessage("Could not load photo from S3");
        assertThatThrownBy(() -> storage.delete(reference))
                .isInstanceOf(PhotoStorageException.class)
                .hasMessage("Could not delete photo from S3");
    }

    @Test
    void refusesLoadWhenReferenceKeyIsOutsideConfiguredPrefix() {
        S3Client s3 = mock(S3Client.class);
        S3PhotoStorageAdapter storage = new S3PhotoStorageAdapter(s3, "packly-photos", "analysis-photos");
        PhotoStorageReference reference = PhotoStorageReference.restore(
                null, "other-photos/secret", "image/jpeg", 1L, "not-a-real-checksum");

        assertThatThrownBy(() -> storage.load(reference))
                .isInstanceOf(PhotoStorageException.class)
                .hasMessage("Photo storage reference is outside the configured prefix")
                .hasMessageNotContaining("packly-photos")
                .hasMessageNotContaining("other-photos/secret");
        verify(s3, never()).headObject(any(HeadObjectRequest.class));
        verify(s3, never()).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    void refusesDeleteWhenReferenceKeyOnlySharesPrefixText() {
        S3Client s3 = mock(S3Client.class);
        S3PhotoStorageAdapter storage = new S3PhotoStorageAdapter(s3, "packly-photos", "analysis-photos");
        PhotoStorageReference reference = PhotoStorageReference.restore(
                null, "analysis-photos-archive/secret", "image/jpeg", 1L, "not-a-real-checksum");

        assertThatThrownBy(() -> storage.delete(reference))
                .isInstanceOf(PhotoStorageException.class)
                .hasMessage("Photo storage reference is outside the configured prefix")
                .hasMessageNotContaining("packly-photos")
                .hasMessageNotContaining("analysis-photos-archive/secret");
        verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));
    }
}
