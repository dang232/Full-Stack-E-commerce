package com.vnshop.orderservice;

import com.vnshop.orderservice.application.InvoiceUseCase;
import com.vnshop.orderservice.domain.Address;
import com.vnshop.orderservice.domain.Invoice;
import com.vnshop.orderservice.domain.InvoiceAccessDeniedException;
import com.vnshop.orderservice.domain.Money;
import com.vnshop.orderservice.domain.Order;
import com.vnshop.orderservice.domain.OrderItem;
import com.vnshop.orderservice.domain.PaymentStatus;
import com.vnshop.orderservice.domain.SubOrder;
import com.vnshop.orderservice.domain.port.out.InvoicePdfRendererPort;
import com.vnshop.orderservice.domain.port.out.InvoiceRepositoryPort;
import com.vnshop.orderservice.domain.port.out.InvoiceStoragePort;
import com.vnshop.orderservice.domain.port.out.OrderRepositoryPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvoiceUseCaseTest {
    private static final Instant GENERATED_AT = Instant.parse("2026-05-10T00:00:00Z");
    private static final byte[] PDF_BYTES = "invoice-pdf".getBytes(StandardCharsets.UTF_8);

    @Test
    void storesGeneratedInvoicePdfAndMetadata() throws Exception {
        FakeOrderRepository orderRepository = new FakeOrderRepository(order("buyer-a", "seller-a"));
        FakeInvoiceRepository invoiceRepository = new FakeInvoiceRepository();
        FakeInvoiceStorage storage = new FakeInvoiceStorage();
        InvoiceUseCase useCase = useCase(orderRepository, invoiceRepository, storage);

        Invoice invoice = useCase.generate(UUID.fromString("00000000-0000-0000-0000-000000000001"), 11L);

        assertThat(invoice.orderId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        assertThat(invoice.subOrderId()).isEqualTo(11L);
        assertThat(invoice.buyerId()).isEqualTo("buyer-a");
        assertThat(invoice.sellerId()).isEqualTo("seller-a");
        assertThat(invoice.objectKey()).startsWith("invoices/00000000-0000-0000-0000-000000000001/11/").endsWith(".pdf");
        assertThat(invoice.objectKey()).doesNotContain("buyer-a");
        assertThat(invoice.checksumSha256()).isEqualTo(sha256Hex(PDF_BYTES));
        assertThat(invoice.version()).isEqualTo(1);
        assertThat(invoice.generatedAt()).isEqualTo(GENERATED_AT);
        assertThat(storage.puts).singleElement().satisfies(put -> {
            assertThat(put.objectKey()).isEqualTo(invoice.objectKey());
            assertThat(put.content()).isEqualTo(PDF_BYTES);
            assertThat(put.checksumSha256()).isEqualTo(invoice.checksumSha256());
        });
    }

    @Test
    void returnsFiveMinuteSignedUrlForAuthorizedBuyerSellerAndAdminFinance() {
        FakeInvoiceStorage storage = new FakeInvoiceStorage();
        Invoice invoice = invoice("buyer-a", "seller-a", "invoices/order-1/11/key.pdf");
        FakeInvoiceRepository invoiceRepository = new FakeInvoiceRepository(invoice);
        InvoiceUseCase useCase = useCase(new FakeOrderRepository(order("buyer-a", "seller-a")), invoiceRepository, storage);

        assertThat(useCase.signedDownloadUrl(invoice.id(), new InvoiceUseCase.InvoiceRequester("buyer-a", null, false)))
                .isEqualTo(URI.create("https://storage.test/invoices/order-1/11/key.pdf"));
        assertThat(useCase.signedDownloadUrl(invoice.id(), new InvoiceUseCase.InvoiceRequester(null, "seller-a", false)))
                .isEqualTo(URI.create("https://storage.test/invoices/order-1/11/key.pdf"));
        assertThat(useCase.signedDownloadUrl(invoice.id(), new InvoiceUseCase.InvoiceRequester(null, null, true)))
                .isEqualTo(URI.create("https://storage.test/invoices/order-1/11/key.pdf"));
        assertThat(storage.downloads).hasSize(3).allSatisfy(download -> assertThat(download.ttl()).isEqualTo(Duration.ofMinutes(5)));
    }

    @Test
    void rejectsUnrelatedBuyerWithForbiddenDomainError() {
        Invoice invoice = invoice("buyer-a", "seller-a", "invoices/order-1/11/key.pdf");
        InvoiceUseCase useCase = useCase(new FakeOrderRepository(order("buyer-a", "seller-a")), new FakeInvoiceRepository(invoice), new FakeInvoiceStorage());

        assertThatThrownBy(() -> useCase.signedDownloadUrl(invoice.id(), new InvoiceUseCase.InvoiceRequester("buyer-b", null, false)))
                .isInstanceOf(InvoiceAccessDeniedException.class)
                .hasMessageContaining("invoice access denied");
    }

    private InvoiceUseCase useCase(FakeOrderRepository orderRepository, FakeInvoiceRepository invoiceRepository, FakeInvoiceStorage storage) {
        InvoicePdfRendererPort renderer = (order, subOrder, version) -> PDF_BYTES;
        return new InvoiceUseCase(orderRepository, invoiceRepository, storage, renderer, Clock.fixed(GENERATED_AT, ZoneOffset.UTC));
    }

    private static Order order(String buyerId, String sellerId) {
        return new Order(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "VNS-20260510-00001",
                buyerId,
                new Address("1 Main", null, "District", "City"),
                List.of(new SubOrder(
                        11L,
                        sellerId,
                        List.of(new OrderItem("product-1", "sku-1", sellerId, "Product", 2, new Money(BigDecimal.valueOf(1000)), null)),
                        com.vnshop.orderservice.domain.FulfillmentStatus.ACCEPTED,
                        Money.ZERO,
                        "STANDARD",
                        null,
                        null
                )),
                Money.ZERO,
                Money.ZERO,
                Money.ZERO,
                "COD",
                PaymentStatus.COMPLETED,
                "idem-1"
        );
    }

    private static Invoice invoice(String buyerId, String sellerId, String objectKey) {
        return new Invoice(UUID.fromString("00000000-0000-0000-0000-000000000010"), UUID.fromString("00000000-0000-0000-0000-000000000001"), 11L, buyerId, sellerId, objectKey, sha256Hex(PDF_BYTES), 1, GENERATED_AT);
    }

    private static String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class FakeOrderRepository implements OrderRepositoryPort {
        private final Order order;

        private FakeOrderRepository(Order order) {
            this.order = order;
        }

        @Override
        public Order save(Order order) {
            return order;
        }

        @Override
        public Optional<Order> findById(UUID orderId) {
            return order.id().equals(orderId) ? Optional.of(order) : Optional.empty();
        }

        @Override
        public Optional<Order> findByOrderNumber(String orderNumber) {
            return Optional.empty();
        }

        @Override
        public Optional<Order> findByIdempotencyKey(String idempotencyKey) {
            return Optional.empty();
        }

        @Override
        public List<Order> findByBuyerId(String buyerId) {
            return List.of();
        }

        @Override
        public Optional<Order> findBySubOrderId(Long subOrderId) {
            return Optional.empty();
        }

        @Override
        public Optional<String> findOrderIdBySubOrderId(Long subOrderId) {
            return Optional.empty();
        }

        @Override
        public List<Order> findBySellerIdAndFulfillmentStatus(String sellerId, com.vnshop.orderservice.domain.FulfillmentStatus status) {
            return List.of();
        }
    }

    private static final class FakeInvoiceRepository implements InvoiceRepositoryPort {
        private final List<Invoice> invoices = new ArrayList<>();

        private FakeInvoiceRepository(Invoice... invoices) {
            this.invoices.addAll(List.of(invoices));
        }

        @Override
        public Invoice save(Invoice invoice) {
            invoices.add(invoice);
            return invoice;
        }

        @Override
        public Optional<Invoice> findById(UUID invoiceId) {
            return invoices.stream().filter(invoice -> invoice.id().equals(invoiceId)).findFirst();
        }

        @Override
        public Optional<Invoice> findBySubOrderId(Long subOrderId) {
            return invoices.stream().filter(invoice -> invoice.subOrderId().equals(subOrderId)).findFirst();
        }
    }

    private static final class FakeInvoiceStorage implements InvoiceStoragePort {
        private final List<Put> puts = new ArrayList<>();
        private final List<Download> downloads = new ArrayList<>();

        @Override
        public void putInvoicePdf(String objectKey, byte[] content, String checksumSha256) {
            puts.add(new Put(objectKey, content, checksumSha256));
        }

        @Override
        public URI signedDownloadUrl(String objectKey, Duration ttl) {
            downloads.add(new Download(objectKey, ttl));
            return URI.create("https://storage.test/" + objectKey);
        }
    }

    private record Put(String objectKey, byte[] content, String checksumSha256) {
    }

    private record Download(String objectKey, Duration ttl) {
    }
}
