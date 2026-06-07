package com.vnshop.invoiceservice.application.gdt;

/**
 * Result of submitting an invoice XML to the GDT transmission API.
 */
public record GdtSubmissionResult(
        boolean success,
        String gdtInvoiceNumber,
        String verificationCode,
        String rejectionReason
) {

    public static GdtSubmissionResult accepted(String gdtInvoiceNumber, String verificationCode) {
        return new GdtSubmissionResult(true, gdtInvoiceNumber, verificationCode, null);
    }

    public static GdtSubmissionResult rejected(String rejectionReason) {
        return new GdtSubmissionResult(false, null, null, rejectionReason);
    }
}
