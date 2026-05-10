package com.vnshop.orderservice.domain.port.out;

import com.vnshop.orderservice.domain.Order;
import com.vnshop.orderservice.domain.SubOrder;

public interface InvoicePdfRendererPort {
    byte[] render(Order order, SubOrder subOrder, int version);
}
