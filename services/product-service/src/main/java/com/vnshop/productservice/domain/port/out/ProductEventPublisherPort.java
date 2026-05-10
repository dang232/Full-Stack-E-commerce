package com.vnshop.productservice.domain.port.out;

import com.vnshop.productservice.domain.ProductEvent;

public interface ProductEventPublisherPort {
    void publish(ProductEvent event);
}
