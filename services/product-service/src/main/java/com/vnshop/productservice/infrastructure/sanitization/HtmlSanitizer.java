package com.vnshop.productservice.infrastructure.sanitization;

import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.springframework.stereotype.Component;

@Component
public class HtmlSanitizer {
    private static final PolicyFactory POLICY = Sanitizers.FORMATTING
            .and(Sanitizers.LINKS)
            .and(Sanitizers.BLOCKS)
            .and(Sanitizers.TABLES);

    public String sanitize(String untrusted) {
        if (untrusted == null) return null;
        return POLICY.sanitize(untrusted);
    }
}
