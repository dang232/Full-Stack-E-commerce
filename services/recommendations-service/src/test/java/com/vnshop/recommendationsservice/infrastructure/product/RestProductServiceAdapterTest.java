package com.vnshop.recommendationsservice.infrastructure.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.vnshop.recommendationsservice.application.ProductProjection;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Drives the RestClient adapter against an in-process JDK
 * {@link HttpServer}. Avoids pulling MockWebServer (not on the classpath of
 * this service) while still exercising the full request → JSON parse →
 * projection mapping path.
 */
class RestProductServiceAdapterTest {

    private HttpServer server;
    private final BlockingQueue<HandlerSpec> queue = new ArrayBlockingQueue<>(8);
    private final BlockingQueue<RecordedRequest> recorded = new ArrayBlockingQueue<>(8);
    private RestProductServiceAdapter adapter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", new QueueHandler(queue, recorded));
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        adapter = new RestProductServiceAdapter(RestClient.builder(), objectMapper, baseUrl);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void findByIdParsesEnvelopedProductResponse() throws Exception {
        Map<String, Object> body = Map.of(
                "id", "p-1",
                "sellerId", "s-1",
                "name", "Phone",
                "categoryId", "phones",
                "price", 1000,
                "originalPrice", 1200,
                "image", "https://cdn/phone.jpg",
                "rating", 4.5,
                "reviewCount", 10,
                "sold", 100
        );
        Map<String, Object> envelope = Map.of(
                "success", true, "message", "ok", "data", body, "errorCode", "",
                "timestamp", "2026-01-01T00:00:00");
        enqueue(200, envelope);

        Optional<ProductProjection> result = adapter.findById("p-1");

        assertThat(result).isPresent();
        ProductProjection projection = result.get();
        assertThat(projection.id()).isEqualTo("p-1");
        assertThat(projection.categoryId()).isEqualTo("phones");
        assertThat(projection.price()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(projection.image()).isEqualTo("https://cdn/phone.jpg");
        RecordedRequest request = takeRequest();
        assertThat(request.path).isEqualTo("/products/p-1");
    }

    @Test
    void findByIdFallsBackToVariantImageAndPriceWhenTopLevelMissing() throws Exception {
        Map<String, Object> variant = Map.of(
                "sku", "sku-1",
                "name", "Default",
                "priceAmount", 850,
                "priceCurrency", "VND",
                "imageUrl", "https://cdn/variant.jpg",
                "stockQuantity", 5
        );
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("id", "p-2");
        body.put("name", "Phone 2");
        body.put("categoryId", "phones");
        body.put("variants", List.of(variant));
        body.put("images", List.of());
        java.util.Map<String, Object> envelope = new java.util.HashMap<>();
        envelope.put("success", true);
        envelope.put("message", "ok");
        envelope.put("data", body);
        envelope.put("errorCode", null);
        envelope.put("timestamp", "x");
        enqueue(200, envelope);

        Optional<ProductProjection> result = adapter.findById("p-2");

        assertThat(result).isPresent();
        assertThat(result.get().price()).isEqualByComparingTo(new BigDecimal("850"));
        assertThat(result.get().image()).isEqualTo("https://cdn/variant.jpg");
    }

    @Test
    void findByIdReturnsEmptyOnNotFound() throws Exception {
        enqueueRaw(404, "{}");

        assertThat(adapter.findById("missing")).isEmpty();
    }

    @Test
    void findByIdReturnsEmptyWhenServerErrors() throws Exception {
        enqueueRaw(503, "{}");

        assertThat(adapter.findById("p-3")).isEmpty();
    }

    @Test
    void findByIdReturnsEmptyWhenDataIsNull() throws Exception {
        java.util.Map<String, Object> envelope = new java.util.HashMap<>();
        envelope.put("success", true);
        envelope.put("message", "ok");
        envelope.put("data", null);
        envelope.put("errorCode", null);
        envelope.put("timestamp", "x");
        enqueue(200, envelope);

        assertThat(adapter.findById("p-4")).isEmpty();
    }

    @Test
    void listByCategoryReturnsContent() throws Exception {
        Map<String, Object> a = Map.of("id", "a", "name", "A", "categoryId", "phones", "price", 100);
        Map<String, Object> b = Map.of("id", "b", "name", "B", "categoryId", "phones", "price", 200);
        Map<String, Object> page = Map.of("content", List.of(a, b), "totalElements", 2,
                "totalPages", 1, "number", 0, "size", 50);
        java.util.Map<String, Object> envelope = new java.util.HashMap<>();
        envelope.put("success", true);
        envelope.put("message", "ok");
        envelope.put("data", page);
        envelope.put("errorCode", null);
        envelope.put("timestamp", "x");
        enqueue(200, envelope);

        List<ProductProjection> result = adapter.listByCategory("phones", 50);

        assertThat(result).extracting(ProductProjection::id).containsExactly("a", "b");
        RecordedRequest request = takeRequest();
        assertThat(request.path).contains("categoryId=phones");
        assertThat(request.path).contains("size=50");
    }

    @Test
    void listByCategoryReturnsEmptyWhenServerErrors() throws Exception {
        enqueueRaw(500, "{}");

        assertThat(adapter.listByCategory("phones", 50)).isEmpty();
    }

    @Test
    void listByCategoryShortCircuitsBlankCategoryAndZeroLimit() {
        // No mock response queued — adapter must not perform any HTTP call.
        assertThat(adapter.listByCategory("  ", 10)).isEmpty();
        assertThat(adapter.listByCategory("phones", 0)).isEmpty();
        assertThat(adapter.listByCategory(null, 10)).isEmpty();
    }

    @Test
    void listByCategoryHandlesEmptyContent() throws Exception {
        Map<String, Object> page = Map.of("content", List.of(), "totalElements", 0);
        java.util.Map<String, Object> envelope = new java.util.HashMap<>();
        envelope.put("success", true);
        envelope.put("message", "ok");
        envelope.put("data", page);
        envelope.put("errorCode", null);
        envelope.put("timestamp", "x");
        enqueue(200, envelope);

        assertThat(adapter.listByCategory("phones", 10)).isEmpty();
    }

    private void enqueue(int status, Object body) throws Exception {
        queue.put(new HandlerSpec(status, objectMapper.writeValueAsBytes(body)));
    }

    private void enqueueRaw(int status, String body) throws Exception {
        queue.put(new HandlerSpec(status, body.getBytes()));
    }

    private RecordedRequest takeRequest() throws InterruptedException {
        RecordedRequest request = recorded.poll(2, TimeUnit.SECONDS);
        if (request == null) {
            throw new AssertionError("No request recorded within timeout");
        }
        return request;
    }

    private record HandlerSpec(int status, byte[] body) {
    }

    private record RecordedRequest(String method, String path) {
    }

    private static final class QueueHandler implements HttpHandler {
        private final BlockingQueue<HandlerSpec> queue;
        private final BlockingQueue<RecordedRequest> recorded;

        QueueHandler(BlockingQueue<HandlerSpec> queue, BlockingQueue<RecordedRequest> recorded) {
            this.queue = queue;
            this.recorded = recorded;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String pathWithQuery = exchange.getRequestURI().getRawPath();
            if (exchange.getRequestURI().getRawQuery() != null) {
                pathWithQuery = pathWithQuery + "?" + exchange.getRequestURI().getRawQuery();
            }
            recorded.add(new RecordedRequest(exchange.getRequestMethod(), pathWithQuery));
            HandlerSpec spec;
            try {
                spec = queue.poll(2, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                exchange.sendResponseHeaders(500, 0);
                exchange.close();
                return;
            }
            if (spec == null) {
                exchange.sendResponseHeaders(500, 0);
                exchange.close();
                return;
            }
            byte[] body = spec.body();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(spec.status(), body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }
    }
}
