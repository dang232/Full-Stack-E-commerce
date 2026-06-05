package com.vnshop.searchservice.infrastructure.elasticsearch;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Document(indexName = "products")
@Setting(settingPath = "/elasticsearch/product-settings.json")
@Getter
@Setter
public class ProductDocument {

    @Id
    private String id;

    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "vietnamese_analyzer"),
            otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword)
    )
    private String name;

    @Field(type = FieldType.Text, analyzer = "vietnamese_analyzer")
    private String description;

    @Field(type = FieldType.Keyword)
    private String categoryId;

    @Field(type = FieldType.Keyword)
    private String categoryName;

    @Field(type = FieldType.Keyword)
    private String brand;

    @Field(type = FieldType.Double)
    private BigDecimal price;

    @Field(type = FieldType.Double)
    private BigDecimal originalPrice;

    @Field(type = FieldType.Float)
    private Float averageRating;

    @Field(type = FieldType.Integer)
    private Integer reviewCount;

    @Field(type = FieldType.Keyword)
    private String sellerId;

    @Field(type = FieldType.Keyword)
    private String sellerName;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Keyword)
    private List<String> imageUrls;

    @Field(type = FieldType.Date)
    private Instant createdAt;

    @Field(type = FieldType.Integer)
    private Integer totalSold;
}
