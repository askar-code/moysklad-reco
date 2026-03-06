package ru.coproducts.moysklad.reco.recommendation;

public class RecommendationDto {

    private final String productId;
    private final String name;
    private final String article;
    private final String productUrl;
    private final long supportCount;
    private final double confidence;

    public RecommendationDto(String productId, String name, String article, String productUrl, long supportCount, double confidence) {
        this.productId = productId;
        this.name = name;
        this.article = article;
        this.productUrl = productUrl;
        this.supportCount = supportCount;
        this.confidence = confidence;
    }

    public String getProductId() {
        return productId;
    }

    public String getName() {
        return name;
    }

    public String getArticle() {
        return article;
    }

    public String getProductUrl() {
        return productUrl;
    }

    public long getSupportCount() {
        return supportCount;
    }

    public double getConfidence() {
        return confidence;
    }
}
