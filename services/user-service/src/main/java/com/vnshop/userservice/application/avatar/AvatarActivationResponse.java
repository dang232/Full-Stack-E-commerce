package com.vnshop.userservice.application.avatar;

import com.vnshop.userservice.domain.BuyerProfile;

public record AvatarActivationResponse(
        BuyerProfile profile,
        String avatarUrl
) {
}
