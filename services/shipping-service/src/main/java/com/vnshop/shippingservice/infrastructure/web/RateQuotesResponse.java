package com.vnshop.shippingservice.infrastructure.web;

import java.util.List;

public record RateQuotesResponse(List<RateQuoteResponse> options) {}
