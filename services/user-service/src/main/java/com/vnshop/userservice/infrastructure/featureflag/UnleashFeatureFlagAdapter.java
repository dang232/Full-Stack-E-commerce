package com.vnshop.userservice.infrastructure.featureflag;

import com.vnshop.userservice.domain.port.out.FeatureFlagPort;
import io.getunleash.Unleash;
import io.getunleash.UnleashContext;
import org.springframework.stereotype.Component;

@Component
public class UnleashFeatureFlagAdapter implements FeatureFlagPort {

    private final Unleash unleash;

    public UnleashFeatureFlagAdapter(Unleash unleash) {
        this.unleash = unleash;
    }

    @Override
    public boolean isEnabled(String flagName) {
        return unleash.isEnabled(flagName);
    }

    @Override
    public boolean isEnabled(String flagName, String userId) {
        UnleashContext context = UnleashContext.builder()
                .userId(userId)
                .build();
        return unleash.isEnabled(flagName, context);
    }
}
