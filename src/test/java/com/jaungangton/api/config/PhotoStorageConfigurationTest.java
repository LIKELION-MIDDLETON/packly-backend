package com.jaungangton.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.jaungangton.api.analysis.DatabasePhotoStorageAdapter;
import com.jaungangton.api.analysis.MigratingPhotoStorageAdapter;
import com.jaungangton.api.analysis.PhotoStoragePort;

import software.amazon.awssdk.services.s3.S3Client;

class PhotoStorageConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PhotoStorageConfiguration.class);

    @Test
    void selectsDatabaseStorageByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(PhotoStoragePort.class);
            assertThat(context.getBean(PhotoStoragePort.class)).isInstanceOf(DatabasePhotoStorageAdapter.class);
            assertThat(context).doesNotHaveBean(S3Client.class);
        });
    }

    @Test
    void selectsS3StorageWithDefaultCredentialChainClient() {
        runner.withPropertyValues(
                "centralton.photo.storage.mode=s3",
                "centralton.photo.storage.region=ap-southeast-2",
                "centralton.photo.storage.bucket=packly-photos",
                "centralton.photo.storage.prefix=analysis-photos")
                .run(context -> {
                    assertThat(context).hasSingleBean(PhotoStoragePort.class);
                    assertThat(context.getBean(PhotoStoragePort.class)).isInstanceOf(MigratingPhotoStorageAdapter.class);
                    assertThat(context).hasSingleBean(S3Client.class);
                });
    }

    @Test
    void failsFastWhenS3BucketIsMissing() {
        runner.withPropertyValues(
                "centralton.photo.storage.mode=s3",
                "centralton.photo.storage.region=ap-southeast-2",
                "centralton.photo.storage.prefix=analysis-photos")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage(
                            "S3_PHOTO_BUCKET is required when PHOTO_STORAGE_MODE=s3");
                });
    }
}
