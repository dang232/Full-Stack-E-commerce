package com.vnshop.userservice.domain.port.out;

public interface FeatureFlagPort {
    boolean isEnabled(String flagName);
    boolean isEnabled(String flagName, String userId);
}
