package com.vnshop.reviewservice.domain.storage;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ObjectValidationResult {
    ObjectQuarantineState quarantineState;
    List<String> failures;

    public boolean active() {
        return quarantineState == ObjectQuarantineState.ACTIVE;
    }
}
