package com.vnshop.userservice.infrastructure.web;

import com.vnshop.userservice.application.WishlistUseCase;
import com.vnshop.userservice.domain.WishlistItem;
import com.vnshop.userservice.infrastructure.config.JwtPrincipalUtil;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/users/me/wishlist")
public class WishlistController {
    private final WishlistUseCase wishlistUseCase;

    public WishlistController(WishlistUseCase wishlistUseCase) {
        this.wishlistUseCase = wishlistUseCase;
    }

    @GetMapping
    public ApiResponse<WishlistResponse> list() {
        List<WishlistItem> items = wishlistUseCase.list(JwtPrincipalUtil.currentUserId());
        return ApiResponse.ok(WishlistResponse.fromDomain(items));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<WishlistToggleResponse> add(@RequestBody WishlistAddRequest request) {
        boolean added = wishlistUseCase.add(JwtPrincipalUtil.currentUserId(), request.productId());
        return ApiResponse.ok(new WishlistToggleResponse(request.productId(), added, true));
    }

    @PostMapping("/toggle")
    public ApiResponse<WishlistToggleResponse> toggle(@RequestBody WishlistAddRequest request) {
        boolean inWishlist = wishlistUseCase.toggle(
                JwtPrincipalUtil.currentUserId(), request.productId());
        return ApiResponse.ok(new WishlistToggleResponse(
                request.productId(), inWishlist, inWishlist));
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<WishlistToggleResponse> remove(@PathVariable String productId) {
        boolean removed = wishlistUseCase.remove(
                JwtPrincipalUtil.currentUserId(), productId);
        return ApiResponse.ok(new WishlistToggleResponse(productId, removed, false));
    }

    @DeleteMapping
    public ApiResponse<WishlistClearResponse> clear() {
        int removed = wishlistUseCase.clear(JwtPrincipalUtil.currentUserId());
        return ApiResponse.ok(new WishlistClearResponse(removed));
    }

    public record WishlistAddRequest(@NotBlank String productId) {}

    public record WishlistResponse(List<String> productIds, List<WishlistEntry> items) {
        static WishlistResponse fromDomain(List<WishlistItem> items) {
            return new WishlistResponse(
                    items.stream().map(WishlistItem::productId).toList(),
                    items.stream()
                            .map(item -> new WishlistEntry(item.productId(), item.createdAt()))
                            .toList());
        }
    }

    public record WishlistEntry(String productId, Instant createdAt) {}

    /**
     * @param productId    the affected product
     * @param changed      true when the call mutated state (added or removed)
     * @param inWishlist   the resulting "is on the wishlist" state for this product
     */
    public record WishlistToggleResponse(String productId, boolean changed, boolean inWishlist) {}

    public record WishlistClearResponse(int removed) {}
}
