package com.vnshop.shippingservice.domain;

public enum TrackingStatus {
    UNKNOWN,
    CREATED,
    PICKING_UP,
    IN_TRANSIT,
    DELIVERED,
    CANCELLED,
    FAILED
}
