package com.vnshop.recommendationsservice.infrastructure.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnshop.recommendationsservice.application.ProductProjection;
import com.vnshop.recommendationsservice.application.ProductServicePort;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * RestClient-based adapter to product-service. Speaks the canonical
 * {@code ApiResponse<T>} envelope used across the BE — see
 * {@code services/product-service/src/main/java/.../ApiResponse.java}.
 *
 * <p>The "list by category" call goes through the catalog endpoint
 * ({@code GET /products?categoryId=...&size=...}) which returns a Spring
 * {@code Page<ProductResponse>}. We deliberately do not propagate the
 * incoming JWT — these endpoints are public on product-service and the
 * recommendations endpoints are public too.
 *
 * <p>Implementation note: the adapter parses responses through Jackson's
 * tree model rather than typed POJO mapping. Generic envelope deserialization
 * with public fields needs explicit field-visibility configuration; using
 * {@link JsonNode} keeps the adapter independent of any Jackson defaults.
 */
@Component
public class RestProductServiceAdapter implements ProductServicePort {
    private static final Logger LOGGER = LoggerFactory.getLogger(RestProductServiceAdapter.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public RestProductServiceAdapter(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${vnshop.recommendations.product-service-url:http://product-service:8082}") String productServiceUrl
    ) {
        this.restClient = restClientBuilder.baseUrl(productServiceUrl).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<ProductProjection> findById(String productId) {
        try {
            String body = restClient.get()
                    .uri("/products/{id}", productId)
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                return Optional.empty();
            }
            JsonNode envelope = objectMapper.readTree(body);
            JsonNode data = envelope.path("data");
            if (data.isMissingNode() || data.isNull()) {
                return Optional.empty();
            }
            return Optional.of(toProjection(data));
        } catch (RestClientResponseException exception) {
            // 404 / 422 just mean "no such product" for our purposes.
            LOGGER.debug("findById({}) failed: {} {}", productId, exception.getStatusCode(), exception.getMessage());
            return Optional.empty();
        } catch (RuntimeException | java.io.IOException exception) {
            LOGGER.warn("findById({}) failed: {}", productId, exception.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<ProductProjection> listByCategory(String categoryId, int limit) {
        if (categoryId == null || categoryId.isBlank() || limit <= 0) {
            return List.of();
        }
        try {
            String body = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/products")
                            .queryParam("categoryId", categoryId)
                            .queryParam("size", limit)
                            .build())
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                return List.of();
            }
            JsonNode envelope = objectMapper.readTree(body);
            JsonNode data = envelope.path("data");
            JsonNode content = data.path("content");
            if (!content.isArray()) {
                return List.of();
            }
            List<ProductProjection> result = new ArrayList<>(content.size());
            for (JsonNode item : content) {
                result.add(toProjection(item));
            }
            return Collections.unmodifiableList(result);
        } catch (RestClientResponseException exception) {
            LOGGER.debug("listByCategory({}) failed: {} {}", categoryId, exception.getStatusCode(), exception.getMessage());
            return List.of();
        } catch (RuntimeException | java.io.IOException exception) {
            LOGGER.warn("listByCategory({}) failed: {}", categoryId, exception.getMessage());
            return List.of();
        }
    }

    private static ProductProjection toProjection(JsonNode body) {
        BigDecimal price = lowestVariantPrice(body);
        BigDecimal originalPrice = decimalOrNull(body.path("originalPrice"));
        String image = textOrNull(body.path("image"));
        if (image == null) {
            image = firstString(body.path("images"));
        }
        if (image == null) {
            JsonNode variants = body.path("variants");
            if (variants.isArray()) {
                for (JsonNode variant : variants) {
                    String variantImage = textOrNull(variant.path("imageUrl"));
                    if (variantImage != null) {
                        image = variantImage;
                        break;
                    }
                }
            }
        }
        return new ProductProjection(
                textOrNull(body.path("id")),
                textOrNull(body.path("sellerId")),
                textOrNull(body.path("name")),
                textOrNull(body.path("categoryId")),
                image,
                price,
                originalPrice,
                intOrNull(body.path("reviewCount")),
                doubleOrNull(body.path("rating")),
                intOrNull(body.path("sold")),
                stringList(body.path("images"))
        );
    }

    private static BigDecimal lowestVariantPrice(JsonNode body) {
        BigDecimal topLevel = decimalOrNull(body.path("price"));
        if (topLevel != null) {
            return topLevel;
        }
        JsonNode variants = body.path("variants");
        if (!variants.isArray()) {
            return null;
        }
        BigDecimal lowest = null;
        for (JsonNode variant : variants) {
            BigDecimal candidate = decimalOrNull(variant.path("priceAmount"));
            if (candidate != null && (lowest == null || candidate.compareTo(lowest) < 0)) {
                lowest = candidate;
            }
        }
        return lowest;
    }

    private static String textOrNull(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String text = node.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static Integer intOrNull(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.asInt();
    }

    private static Double doubleOrNull(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.asDouble();
    }

    private static BigDecimal decimalOrNull(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        try {
            return new BigDecimal(node.asText());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String firstString(JsonNode array) {
        if (!array.isArray()) {
            return null;
        }
        for (JsonNode element : array) {
            String text = textOrNull(element);
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    private static List<String> stringList(JsonNode array) {
        if (!array.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>(array.size());
        for (JsonNode element : array) {
            String text = textOrNull(element);
            if (text != null) {
                result.add(text);
            }
        }
        return List.copyOf(result);
    }
}
