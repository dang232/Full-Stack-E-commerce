package com.vnshop.inventoryservice.domain.port.out;

import com.vnshop.inventoryservice.domain.FlashSaleCampaign;
import java.time.Instant;
import java.util.List;

/**
 * Read-only access to flash-sale campaign rows. Active = {@code active=true}
 * AND {@code startsAt <= now} AND {@code endsAt > now}.
 */
public interface FlashSaleCampaignPort {
    List<FlashSaleCampaign> findActiveAt(Instant now);
}
