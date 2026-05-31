package com.vnshop.inventoryservice.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FlashSaleCampaignJpaSpringDataRepository
        extends JpaRepository<FlashSaleCampaignJpaEntity, UUID> {

    @Query("select c from FlashSaleCampaignJpaEntity c "
            + "where c.active = true and c.startsAt <= :now and c.endsAt > :now "
            + "order by c.endsAt asc")
    List<FlashSaleCampaignJpaEntity> findActiveAt(@Param("now") Instant now);
}
