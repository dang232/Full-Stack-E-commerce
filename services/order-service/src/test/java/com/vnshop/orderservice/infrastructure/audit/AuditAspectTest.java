package com.vnshop.orderservice.infrastructure.audit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AuditAspectTest {

    private AuditLogRepository repository;
    private AuditAspect aspect;

    @BeforeEach
    void setUp() {
        repository = mock(AuditLogRepository.class);
        aspect = new AuditAspect(repository);
    }

    @Test
    void shouldPersistAuditEntry_whenAnnotatedMethodSucceeds() throws Throwable {
        var joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn(null);

        var audited = mock(Audited.class);
        when(audited.action()).thenReturn("CREATE_ORDER");
        when(audited.resourceType()).thenReturn("Order");

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        aspect.audit(joinPoint, audited);

        var captor = ArgumentCaptor.forClass(AuditLogJpaEntity.class);
        verify(repository).save(captor.capture());

        AuditLogJpaEntity saved = captor.getValue();
        assertThat(saved.getAction()).isEqualTo("CREATE_ORDER");
        assertThat(saved.getResourceType()).isEqualTo("Order");
        assertThat(saved.getServiceName()).isEqualTo("order-service");
        assertThat(saved.getTimestamp()).isNotNull();
        assertThat(saved.getUserId()).isEqualTo("anonymous");
    }

    @Test
    void shouldNotBreakBusinessFlow_whenAuditPersistenceFails() throws Throwable {
        var joinPoint = mock(ProceedingJoinPoint.class);
        var expectedResult = "business-result";
        when(joinPoint.proceed()).thenReturn(expectedResult);

        var audited = mock(Audited.class);
        when(audited.action()).thenReturn("CANCEL_ORDER");
        when(audited.resourceType()).thenReturn("Order");

        when(repository.save(any())).thenThrow(new RuntimeException("DB down"));

        Object result = aspect.audit(joinPoint, audited);

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    void shouldExtractResourceId_whenResultHasGetIdMethod() throws Throwable {
        var joinPoint = mock(ProceedingJoinPoint.class);
        var resultWithId = new ResultWithId("order-123");
        when(joinPoint.proceed()).thenReturn(resultWithId);

        var audited = mock(Audited.class);
        when(audited.action()).thenReturn("CREATE_ORDER");
        when(audited.resourceType()).thenReturn("Order");

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        aspect.audit(joinPoint, audited);

        var captor = ArgumentCaptor.forClass(AuditLogJpaEntity.class);
        verify(repository).save(captor.capture());

        assertThat(captor.getValue().getResourceId()).isEqualTo("order-123");
    }

    // Test helper
    static class ResultWithId {
        private final String id;
        ResultWithId(String id) { this.id = id; }
        public String getId() { return id; }
    }
}
