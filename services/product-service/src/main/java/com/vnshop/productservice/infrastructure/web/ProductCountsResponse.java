package com.vnshop.productservice.infrastructure.web;

import java.util.Map;

public record ProductCountsResponse(Map<String, Long> counts) {
}
