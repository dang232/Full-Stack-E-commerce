package com.vnshop.orderservice.infrastructure.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.MethodParameter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.io.IOException;
import java.time.Duration;

@Component
@ControllerAdvice
public class IdempotencyFilter implements HandlerInterceptor, ResponseBodyAdvice<Object> {
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String CACHE_KEY_PREFIX = "idempotency:";
    private static final String REQUEST_CACHE_KEY_ATTRIBUTE = IdempotencyFilter.class.getName() + ".cacheKey";
    private static final String PLACEHOLDER = "__PROCESSING__";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public IdempotencyFilter(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${vnshop.idempotency.ttl:24h}") Duration ttl
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String idempotencyKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return true;
        }

        String cacheKey = CACHE_KEY_PREFIX + idempotencyKey;
        try {
            String cachedResponse = redisTemplate.opsForValue().get(cacheKey);
            if (cachedResponse != null && !PLACEHOLDER.equals(cachedResponse)) {
                CachedResponse cached = objectMapper.readValue(cachedResponse, CachedResponse.class);
                response.setStatus(cached.status());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(cached.body());
                return false;
            }
            redisTemplate.opsForValue().setIfAbsent(cacheKey, PLACEHOLDER, ttl);
            request.setAttribute(REQUEST_CACHE_KEY_ATTRIBUTE, cacheKey);
        } catch (RuntimeException exception) {
            request.setAttribute(REQUEST_CACHE_KEY_ATTRIBUTE, cacheKey);
        }
        return true;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            Object cacheKey = servletRequest.getServletRequest().getAttribute(REQUEST_CACHE_KEY_ATTRIBUTE);
            if (cacheKey instanceof String key) {
                storeResponse(key, body, response);
            }
        }
        return body;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        Object cacheKey = request.getAttribute(REQUEST_CACHE_KEY_ATTRIBUTE);
        if (exception != null && cacheKey instanceof String key) {
            try {
                redisTemplate.delete(key);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void storeResponse(String cacheKey, Object body, ServerHttpResponse response) {
        try {
            int status = response instanceof ServletServerHttpResponse servletResponse
                    ? servletResponse.getServletResponse().getStatus()
                    : 200;
            String responseBody = body instanceof String text ? text : objectMapper.writeValueAsString(body);
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(new CachedResponse(status, responseBody)), ttl);
        } catch (RuntimeException | JsonProcessingException ignored) {
        }
    }

    private record CachedResponse(int status, String body) {
    }
}
