package com.vnshop.reviewservice.infrastructure.config;

import com.vnshop.reviewservice.application.AnswerQuestionUseCase;
import com.vnshop.reviewservice.application.AskQuestionUseCase;
import com.vnshop.reviewservice.application.CreateReviewUseCase;
import com.vnshop.reviewservice.application.GetProductReviewsUseCase;
import com.vnshop.reviewservice.application.GetQuestionsUseCase;
import com.vnshop.reviewservice.application.ModerateReviewUseCase;
import com.vnshop.reviewservice.application.VoteHelpfulUseCase;
import com.vnshop.reviewservice.application.image.ReviewImageUploadService;
import com.vnshop.reviewservice.application.storage.ObjectValidationPolicy;
import com.vnshop.reviewservice.application.storage.ObjectValidationService;
import com.vnshop.reviewservice.domain.port.out.ObjectMetadataRepositoryPort;
import com.vnshop.reviewservice.domain.port.out.ObjectStoragePort;
import com.vnshop.reviewservice.domain.port.out.ReviewRepositoryPort;
import com.vnshop.reviewservice.domain.storage.ObjectStorageClass;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {
    @Bean
    CreateReviewUseCase createReviewUseCase(ReviewRepositoryPort reviewRepositoryPort) {
        return new CreateReviewUseCase(reviewRepositoryPort);
    }

    @Bean
    GetProductReviewsUseCase getProductReviewsUseCase(ReviewRepositoryPort reviewRepositoryPort) {
        return new GetProductReviewsUseCase(reviewRepositoryPort);
    }

    @Bean
    ModerateReviewUseCase moderateReviewUseCase(ReviewRepositoryPort reviewRepositoryPort) {
        return new ModerateReviewUseCase(reviewRepositoryPort);
    }

    @Bean
    VoteHelpfulUseCase voteHelpfulUseCase(ReviewRepositoryPort reviewRepositoryPort) {
        return new VoteHelpfulUseCase(reviewRepositoryPort);
    }

    @Bean
    AskQuestionUseCase askQuestionUseCase(ReviewRepositoryPort reviewRepositoryPort) {
        return new AskQuestionUseCase(reviewRepositoryPort);
    }

    @Bean
    AnswerQuestionUseCase answerQuestionUseCase(ReviewRepositoryPort reviewRepositoryPort) {
        return new AnswerQuestionUseCase(reviewRepositoryPort);
    }

    @Bean
    GetQuestionsUseCase getQuestionsUseCase(ReviewRepositoryPort reviewRepositoryPort) {
        return new GetQuestionsUseCase(reviewRepositoryPort);
    }

    @Bean
    ObjectValidationService reviewImageObjectValidationService() {
        return new ObjectValidationService(ObjectValidationPolicy.builder()
                .storageClass(ObjectStorageClass.REVIEW_IMAGE)
                .maxBytes(5 * 1024 * 1024)
                .allowedContentTypes(Set.of("image/jpeg", "image/png", "image/webp"))
                .maxImageWidth(4096)
                .maxImageHeight(4096)
                .build());
    }

    @Bean
    ReviewImageUploadService reviewImageUploadService(ReviewRepositoryPort reviewRepositoryPort,
            ObjectStoragePort objectStoragePort, ObjectMetadataRepositoryPort objectMetadataRepositoryPort,
            ObjectValidationService reviewImageObjectValidationService) {
        return new ReviewImageUploadService(reviewRepositoryPort, objectStoragePort, objectMetadataRepositoryPort,
                reviewImageObjectValidationService);
    }
}
