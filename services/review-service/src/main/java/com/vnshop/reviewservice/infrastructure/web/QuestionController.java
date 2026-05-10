package com.vnshop.reviewservice.infrastructure.web;

import com.vnshop.reviewservice.application.AnswerQuestionUseCase;
import com.vnshop.reviewservice.application.AskQuestionUseCase;
import com.vnshop.reviewservice.application.GetQuestionsUseCase;
import com.vnshop.reviewservice.domain.ProductQuestion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/questions")
public class QuestionController {
    private final GetQuestionsUseCase getQuestionsUseCase;
    private final AskQuestionUseCase askQuestionUseCase;
    private final AnswerQuestionUseCase answerQuestionUseCase;

    public QuestionController(GetQuestionsUseCase getQuestionsUseCase, AskQuestionUseCase askQuestionUseCase,
            AnswerQuestionUseCase answerQuestionUseCase) {
        this.getQuestionsUseCase = getQuestionsUseCase;
        this.askQuestionUseCase = askQuestionUseCase;
        this.answerQuestionUseCase = answerQuestionUseCase;
    }

    @GetMapping("/product/{productId}")
    public List<QuestionResponse> byProduct(@PathVariable String productId) {
        return getQuestionsUseCase.get(productId).stream().map(QuestionResponse::fromDomain).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionResponse ask(@Valid @RequestBody AskQuestionRequest request) {
        return QuestionResponse.fromDomain(askQuestionUseCase.ask(request.productId(), request.buyerId(), request.question()));
    }

    @PutMapping("/{id}/answer")
    public QuestionResponse answer(@PathVariable String id, @Valid @RequestBody AnswerQuestionRequest request) {
        return QuestionResponse.fromDomain(answerQuestionUseCase.answer(id, request.answer()));
    }

    public record AskQuestionRequest(@NotBlank String productId, @NotBlank String buyerId,
            @NotBlank @Size(max = 1000) String question) {
    }

    public record AnswerQuestionRequest(@NotBlank @Size(max = 1000) String answer) {
    }

    public record QuestionResponse(String questionId, String productId, String buyerId, String question,
            String answer, Instant answeredAt, Instant createdAt) {
        static QuestionResponse fromDomain(ProductQuestion question) {
            return new QuestionResponse(question.questionId(), question.productId(), question.buyerId(),
                    question.question(), question.answer(), question.answeredAt(), question.createdAt());
        }
    }
}
