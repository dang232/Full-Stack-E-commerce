package com.vnshop.orderservice.infrastructure.web;

import java.net.URI;

public record DownloadUrlResponse(URI url, long expiresInSeconds) {
}
