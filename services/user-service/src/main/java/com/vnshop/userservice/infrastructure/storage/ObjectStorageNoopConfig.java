package com.vnshop.userservice.infrastructure.storage;

import com.vnshop.userservice.domain.port.out.ObjectStoragePort;
import com.vnshop.userservice.domain.storage.AvatarObjectMetadata;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fallback {@link ObjectStoragePort} for environments where MinIO/R2 isn't
 * configured (unit tests, slim local stacks). Returns deterministic
 * placeholder URIs so use cases that depend on the port still wire, but
 * nothing actually leaves the process. The real {@code S3ObjectStorageAdapter}
 * bean takes precedence when {@code vnshop.user-storage.enabled=true}.
 */
@Configuration
public class ObjectStorageNoopConfig {

    @Bean
    @ConditionalOnMissingBean(ObjectStoragePort.class)
    ObjectStoragePort noopObjectStoragePort() {
        return new NoopObjectStoragePort();
    }

    static final class NoopObjectStoragePort implements ObjectStoragePort {
        private static final URI PLACEHOLDER = URI.create("about:blank");

        @Override
        public URI getSignedUploadUrl(String key, AvatarObjectMetadata metadata, Duration ttl) {
            return PLACEHOLDER;
        }

        @Override
        public URI publicUrl(String key) {
            return PLACEHOLDER;
        }

        @Override
        public void deleteObject(String key) {
            // no-op
        }

        @Override
        public Optional<AvatarObjectMetadata> headObject(String key) {
            return Optional.empty();
        }
    }
}
