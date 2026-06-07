package com.vnshop.invoiceservice.application.xml;

/**
 * Thrown when TKHDon XML generation or XSD validation fails.
 */
public class InvoiceXmlException extends RuntimeException {

    public InvoiceXmlException(String message, Throwable cause) {
        super(message, cause);
    }
}
