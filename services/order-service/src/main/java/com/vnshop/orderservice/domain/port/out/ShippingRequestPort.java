package com.vnshop.orderservice.domain.port.out;

import com.vnshop.orderservice.domain.Address;
import com.vnshop.orderservice.domain.SubOrder;

public interface ShippingRequestPort {
    void requestShipping(String orderId, SubOrder subOrder, Address shippingAddress);
}
