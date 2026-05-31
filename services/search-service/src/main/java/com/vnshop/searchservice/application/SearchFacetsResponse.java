package com.vnshop.searchservice.application;

import java.util.List;

public record SearchFacetsResponse(
        List<FacetEntry> categories,
        List<FacetEntry> brands
) {
    public record FacetEntry(String key, long count) {}
}
