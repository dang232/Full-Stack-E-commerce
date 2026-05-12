package com.vnshop.productservice.infrastructure.web.review;

import com.vnshop.productservice.infrastructure.web.ApiResponse;
import com.vnshop.productservice.application.review.AnswerQuestionUseCase;
import com.vnshop.productservice.application.review.AskQuestionCommand;
import com.vnshop.productservice.application.review.AskQuestionUseCase;
import com.vnshop.productservice.application.review.GetQuestionsUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

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
    public ApiResponse<List<QuestionResponse>> byProduct(@PathVariable String productId) {
        return ApiResponse.ok(getQuestionsUseCase.get(productId).stream().map(QuestionResponse::fromDomain).toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<QuestionResponse> ask(@Valid @RequestBody AskQuestionRequest request) {
        return ApiResponse.ok(QuestionResponse.fromDomain(askQuestionUseCase.ask(new AskQuestionCommand(request.productId(), request.buyerId(), request.question()))));
    }

    @PutMapping("/{id}/answer")
    public ApiResponse<QuestionResponse> answer(@PathVariable UUID id, @Valid @RequestBody AnswerQuestionRequest request) {
        return ApiResponse.ok(QuestionResponse.fromDomain(answerQuestionUseCase.answer(id, request.answer())));
    }
}
