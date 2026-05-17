package com.vnshop.shippingservice.domain.model;

import java.util.List;

/**
 * Snapshot of a shipment as reported by the carrier. {@code events} is the
 * full timeline (most recent last). The compact constructor copies the list
 * defensively so callers can't mutate the record's state, and it accepts
 * null as "no events known yet" without forcing every adapter to allocate.
 */
public record TrackingInfo(
        CarrierCode carrier,
        String trackingCode,
        String status,
        String statusDescription,
        String updatedAt,
        List<TrackingEvent> events) {

    public TrackingInfo {
        events = events == null ? List.of() : List.copyOf(events);
    }

    /** Backward-compat constructor for adapters that don't know about events yet. */
    public TrackingInfo(
            CarrierCode carrier,
            String trackingCode,
            String status,
            String statusDescription,
            String updatedAt) {
        this(carrier, trackingCode, status, statusDescription, updatedAt, List.of());
    }
}
