package com.vnshop.orderservice.infrastructure.product;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * Declarative HTTP client for product-service. Spring generates the proxy at
 * startup via {@link ProductHttpClientConfig}; callers never instantiate this
 * directly.
 *
 * <p>Returns the raw JSON body so that {@link ProductCatalogAdapter} can apply
 * its existing null-safe mapping and domain-exception translation without
 * duplicating that logic here.
 */
@HttpExchange
public interface ProductHttpClient {

    @GetExchange("/products/{productId}")
    String getProduct(@PathVariable String productId);
}
