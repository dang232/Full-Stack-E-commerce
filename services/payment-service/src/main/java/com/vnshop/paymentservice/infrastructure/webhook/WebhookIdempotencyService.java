package com.vnshop.paymentservice.infrastructure.webhook;

import com.vnshop.paymentservice.infrastructure.persistence.PendingWebhookJpaEntity;
import com.vnshop.paymentservice.infrastructure.persistence.PendingWebhookSpringDataRepository;
import com.vnshop.paymentservice.infrastructure.persistence.ProcessedWebhookJpaEntity;
import com.vnshop.paymentservice.infrastructure.persistence.ProcessedWebhookSpringDataRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Deduplicates inbound provider webhooks using the {@code processed_webhooks} table.
 * A (webhookId, provider) pair is the dedup key — the same event delivered twice
 * is idempotent: the second delivery returns immediately without re-processing.
 *
 * <p>Failures during processing are stored in {@code pending_webhooks} for
 * scheduled retry with exponential back-off (1 min → 5 min → 30 min).
 */
@Service
public class WebhookIdempotencyService {

    /** Back-off schedule in seconds: attempt 1 waits 60 s, 2 waits 300 s, 3 waits 1800 s. */
    private static final long[] BACKOFF_SECONDS = {60L, 300L, 1800L};

    private final ProcessedWebhookSpringDataRepository processedRepo;
    private final PendingWebhookSpringDataRepository pendingRepo;

    public WebhookIdempotencyService(
            ProcessedWebhookSpringDataRepository processedRepo,
            PendingWebhookSpringDataRepository pendingRepo) {
        this.processedRepo = Objects.requireNonNull(processedRepo, "processedRepo is required");
        this.pendingRepo = Objects.requireNonNull(pendingRepo, "pendingRepo is required");
    }

    /**
     * Returns {@code true} when this (webhookId, provider) pair has already been
     * successfully processed and the caller should return 200 immediately.
     */
    @Transactional(readOnly = true)
    public boolean isAlreadyProcessed(String webhookId, String provider) {
        return processedRepo.existsByWebhookIdAndProvider(webhookId, provider);
    }

    /**
     * Records a successfully processed webhook. Uses {@code REQUIRES_NEW} so a later
     * rollback in the outer tx does not un-mark the event (the provider already
     * received 200).
     *
     * <p>A {@link DataIntegrityViolationException} from a concurrent duplicate insert
     * is swallowed: the event was processed, so the idempotency guarantee still holds.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessed(String webhookId, String provider, String eventType) {
        try {
            processedRepo.save(new ProcessedWebhookJpaEntity(webhookId, provider, eventType, Instant.now()));
        } catch (DataIntegrityViolationException ignored) {
            // Concurrent insert of the same key — already processed, nothing to do.
        }
    }

    /**
     * Stores a failed webhook for later retry. The first retry fires after 1 minute.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void storePendingForRetry(String webhookId, String provider, String eventType, String payload) {
        PendingWebhookJpaEntity entity = new PendingWebhookJpaEntity(webhookId, provider, eventType, payload);
        entity.setNextRetryAt(Instant.now().plusSeconds(BACKOFF_SECONDS[0]));
        pendingRepo.save(entity);
    }

    /**
     * Returns the next batch of retryable pending entries.
     * Called exclusively by {@link WebhookRetryScheduler}.
     */
    @Transactional(readOnly = true)
    public List<PendingWebhookJpaEntity> findRetryable(int batchSize) {
        return pendingRepo.findRetryable(Instant.now(), PageRequest.of(0, batchSize));
    }

    /**
     * Advances the retry counter and schedules the next attempt, or marks the
     * record {@code FAILED} when {@code maxAttempts} is exhausted.
     */
    @Transactional
    public void recordRetryOutcome(PendingWebhookJpaEntity entity, boolean succeeded) {
        if (succeeded) {
            entity.setStatus("PROCESSED");
            pendingRepo.save(entity);
            return;
        }

        int next = entity.getAttempts() + 1;
        entity.setAttempts(next);

        if (next >= entity.getMaxAttempts()) {
            entity.setStatus("FAILED");
        } else {
            long backoff = next < BACKOFF_SECONDS.length
                    ? BACKOFF_SECONDS[next]
                    : BACKOFF_SECONDS[BACKOFF_SECONDS.length - 1];
            entity.setNextRetryAt(Instant.now().plusSeconds(backoff));
        }
        pendingRepo.save(entity);
    }
}
