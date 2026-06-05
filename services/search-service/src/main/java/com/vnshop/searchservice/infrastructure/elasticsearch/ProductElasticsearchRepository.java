package com.vnshop.searchservice.infrastructure.elasticsearch;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface ProductElasticsearchRepository extends ElasticsearchRepository<ProductDocument, String> {
    List<ProductDocument> findByStatus(String status);
    List<ProductDocument> findByCategoryId(String categoryId);
}
