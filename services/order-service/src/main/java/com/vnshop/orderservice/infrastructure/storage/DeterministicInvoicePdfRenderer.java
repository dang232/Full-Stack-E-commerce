package com.vnshop.orderservice.infrastructure.storage;

import com.vnshop.orderservice.domain.Order;
import com.vnshop.orderservice.domain.SubOrder;
import com.vnshop.orderservice.domain.port.out.InvoicePdfRendererPort;

import java.nio.charset.StandardCharsets;

public class DeterministicInvoicePdfRenderer implements InvoicePdfRendererPort {
    @Override
    public byte[] render(Order order, SubOrder subOrder, int version) {
        return ("PDF invoice\n"
                + "version=" + version + "\n"
                + "orderId=" + order.id() + "\n"
                + "subOrderId=" + subOrder.id() + "\n"
                + "buyerId=" + order.buyerId() + "\n"
                + "sellerId=" + subOrder.sellerId() + "\n"
                + "total=" + subOrder.itemsTotal().amount() + " " + subOrder.itemsTotal().currency() + "\n")
                .getBytes(StandardCharsets.UTF_8);
    }
}
