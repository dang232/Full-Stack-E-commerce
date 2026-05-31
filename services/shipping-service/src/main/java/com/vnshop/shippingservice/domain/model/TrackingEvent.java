package com.vnshop.shippingservice.domain.model;

/**
 * One step in a shipment's tracking timeline. Carriers expose this as a list
 * of state transitions (created -> picked up -> delivering -> delivered).
 * {@code at} stays a String to mirror {@link TrackingInfo#updatedAt()} so we
 * pass through whatever ISO timestamp the carrier returns without parsing.
 */
public record TrackingEvent(
        String at,
        String status,
        String location,
        String note) {
}
