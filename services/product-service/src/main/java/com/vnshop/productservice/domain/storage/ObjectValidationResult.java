package com.vnshop.productservice.domain.storage;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ObjectValidationResult {
    ObjectQuarantineState quarantineState;
    List<String> failures;

    public ObjectQuarantineState quarantineState() {
        return quarantineState;
    }

    public List<String> failures() {
        return failures;
    }

    public boolean active() {
        return quarantineState == ObjectQuarantineState.ACTIVE;
    }
}
