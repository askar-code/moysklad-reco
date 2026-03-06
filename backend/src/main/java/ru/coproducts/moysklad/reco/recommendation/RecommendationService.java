package ru.coproducts.moysklad.reco.recommendation;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.coproducts.moysklad.reco.domain.AccountInstallation;
import ru.coproducts.moysklad.reco.domain.AccountInstallationService;
import ru.coproducts.moysklad.reco.domain.AccountSettings;
import ru.coproducts.moysklad.reco.domain.AccountSettingsService;
import ru.coproducts.moysklad.reco.jsonapi.JsonApiClient;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    private static final long PRODUCT_CACHE_TTL_MILLIS = ChronoUnit.MINUTES.getDuration().toMillis() * 5;
    private static final int PRODUCT_CACHE_CLEANUP_INTERVAL = 256;

    private static final DateTimeFormatter MOMENT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private final RecommendationPairRepository pairRepository;
    private final BaseProductStatsRepository baseStatsRepository;
    private final AccountInstallationService installationService;
    private final AccountSettingsService settingsService;
    private final JsonApiClient jsonApiClient;
    private final RecommendationDeltaJdbcRepository deltaJdbcRepository;
    private final Map<String, CachedProductInfo> productInfoCache = new ConcurrentHashMap<>();
    private final AtomicInteger productCacheReads = new AtomicInteger();
    private final Map<String, CachedBaseProductId> baseProductResolutionCache = new ConcurrentHashMap<>();
    private final AtomicInteger baseProductResolutionReads = new AtomicInteger();

    public RecommendationService(RecommendationPairRepository pairRepository,
                                 BaseProductStatsRepository baseStatsRepository,
                                 AccountInstallationService installationService,
                                 AccountSettingsService settingsService,
                                 JsonApiClient jsonApiClient,
                                 RecommendationDeltaJdbcRepository deltaJdbcRepository) {
        this.pairRepository = pairRepository;
        this.baseStatsRepository = baseStatsRepository;
        this.installationService = installationService;
        this.settingsService = settingsService;
        this.jsonApiClient = jsonApiClient;
        this.deltaJdbcRepository = deltaJdbcRepository;
    }

    @Transactional(readOnly = true)
    public List<RecommendationDto> getRecommendations(String accountId,
                                                      String baseProductId,
                                                      Integer limitOverride,
                                                      Integer minSupportOverride) {
        AccountSettings settings = settingsService.getOrDefault(accountId);
        long activeSnapshotVersion = settings.getActiveRecommendationVersion();
        int limit = resolveLimit(settings, limitOverride);
        int minSupport = resolveMinSupport(settings, minSupportOverride);

        List<RecommendationPair> cached = pairRepository
                .findByAccountIdAndSnapshotVersionAndBaseProductIdOrderBySupportCountDesc(
                        accountId,
                        activeSnapshotVersion,
                        baseProductId
                );

        if (cached.isEmpty()) {
            Optional<AccountInstallation> installationOpt = installationService.findActiveByAccountId(accountId);
            if (installationOpt.isPresent()) {
                String resolvedBaseProductId = resolveBaseProductId(accountId, installationOpt.get().getAccessToken(), baseProductId);
                if (!resolvedBaseProductId.equals(baseProductId)) {
                    cached = pairRepository.findByAccountIdAndSnapshotVersionAndBaseProductIdOrderBySupportCountDesc(
                            accountId,
                            activeSnapshotVersion,
                            resolvedBaseProductId
                    );
                    if (!cached.isEmpty()) {
                        log.info("Resolved assortmentId {} -> {} for accountId={} recommendations lookup",
                                baseProductId, resolvedBaseProductId, accountId);
                    }
                }
            }
        }

        log.info("Recommendation lookup accountId={} productId={} cachedPairs={} minSupport={} limit={}",
                accountId, baseProductId, cached.size(), minSupport, limit);

        if (cached.isEmpty()) {
            return List.of();
        }

        List<RecommendationPair> filtered = cached.stream()
                .filter(pair -> pair.getSupportCount() >= minSupport)
                .limit(limit)
                .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            log.info("Recommendation lookup accountId={} productId={} produced 0 pairs after minSupport filter",
                    accountId, baseProductId);
            return List.of();
        }

        return enrichWithProductInfo(accountId, filtered);
    }

    private String extractIdFromMetaHref(String href) {
        int idx = href.lastIndexOf('/');
        if (idx < 0 || idx == href.length() - 1) {
            return null;
        }
        return href.substring(idx + 1);
    }

    private List<RecommendationDto> enrichWithProductInfo(String accountId, List<RecommendationPair> pairs) {
        Optional<AccountInstallation> installationOpt = installationService.findActiveByAccountId(accountId);
        if (installationOpt.isEmpty()) {
            return List.of();
        }

        AccountInstallation installation = installationOpt.get();
        String token = installation.getAccessToken();

        return pairs.stream()
                .sorted(Comparator.comparingLong(RecommendationPair::getSupportCount).reversed())
                .map(pair -> {
                    CachedProductInfo productInfo = getCachedProductInfo(accountId, token, pair.getRecommendedProductId());
                    String name = productInfo != null ? productInfo.name() : "";
                    String article = productInfo != null ? productInfo.article() : "";
                    String productUrl = productInfo != null ? productInfo.productUrl() : "";

                    return new RecommendationDto(
                            pair.getRecommendedProductId(),
                            name,
                            article,
                            productUrl,
                            pair.getSupportCount(),
                            pair.getConfidence()
                    );
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void processNewDocumentsForAccount(String accountId) {
        Optional<AccountInstallation> installationOpt = installationService.findActiveByAccountId(accountId);
        if (installationOpt.isEmpty()) {
            log.warn("No active installation for accountId={}, cannot load new documents", accountId);
            return;
        }

        AccountInstallation installation = installationOpt.get();
        String token = installation.getAccessToken();

        AccountSettings settings = settingsService.getOrDefault(accountId);
        long activeSnapshotVersion = settings.getActiveRecommendationVersion();

        Instant now = Instant.now();
        Instant demandFrom = settings.getLastProcessedDemandMoment();
        if (demandFrom == null) {
            demandFrom = paddedAnalysisWindowStart(now, settings.getAnalysisDays());
        }
        Instant retailFrom = settings.getLastProcessedRetailDemandMoment();
        if (retailFrom == null) {
            retailFrom = paddedAnalysisWindowStart(now, settings.getAnalysisDays());
        }
        Instant effectiveTo = paddedAnalysisWindowEnd(now);

        boolean processedDemand = processNewDocuments(token, accountId, activeSnapshotVersion, demandFrom, effectiveTo, false);
        boolean processedRetail = processNewDocuments(token, accountId, activeSnapshotVersion, retailFrom, effectiveTo, true);

        if (processedDemand) {
            settings.setLastProcessedDemandMoment(effectiveTo);
        }
        if (processedRetail) {
            settings.setLastProcessedRetailDemandMoment(effectiveTo);
        }
        settingsService.save(settings);
    }

    public void rebuildRecommendationsForAccount(String accountId) {
        Optional<AccountInstallation> installationOpt = installationService.findActiveByAccountId(accountId);
        if (installationOpt.isEmpty()) {
            log.warn("No active installation for accountId={}, cannot rebuild recommendations", accountId);
            return;
        }

        AccountInstallation installation = installationOpt.get();
        String token = installation.getAccessToken();
        AccountSettings settings = settingsService.getOrDefault(accountId);
        long stagedSnapshotVersion = nextSnapshotVersion();

        Instant now = Instant.now();
        Instant from = paddedAnalysisWindowStart(now, settings.getAnalysisDays());
        Instant effectiveTo = paddedAnalysisWindowEnd(now);

        try {
            processDocumentsInRange(token, accountId, stagedSnapshotVersion, from, effectiveTo, false);
            processDocumentsInRange(token, accountId, stagedSnapshotVersion, from, effectiveTo, true);

            settings.setActiveRecommendationVersion(stagedSnapshotVersion);
            settings.setLastProcessedDemandMoment(effectiveTo);
            settings.setLastProcessedRetailDemandMoment(effectiveTo);
            settingsService.save(settings);
            deltaJdbcRepository.deleteOtherSnapshots(accountId, stagedSnapshotVersion);
        } catch (Exception e) {
            deltaJdbcRepository.deleteSnapshot(accountId, stagedSnapshotVersion);
            throw e;
        }
    }

    private boolean processNewDocuments(String token,
                                        String accountId,
                                        long snapshotVersion,
                                        Instant from,
                                        Instant to,
                                        boolean retail) {
        return processDocumentsInRange(token, accountId, snapshotVersion, from, to, retail);
    }

    private boolean processDocumentsInRange(String token,
                                            String accountId,
                                            long snapshotVersion,
                                            Instant from,
                                            Instant to,
                                            boolean retail) {
        String filter = "moment>" + formatMoment(from)
                + ";moment<=" + formatMoment(to)
                + ";isDeleted=false";

        int pageSize = 100;
        int offset = 0;

        Map<String, Long> baseDocsDelta = new HashMap<>();
        Map<String, Long> supportDelta = new HashMap<>();
        long processedDocs = 0L;

        while (true) {
            JsonNode page = retail
                    ? jsonApiClient.fetchRetailDemandsPage(token, filter, pageSize, offset)
                    : jsonApiClient.fetchDemandsPage(token, filter, pageSize, offset);
            if (page == null || !page.has("rows") || !page.path("rows").isArray()) {
                break;
            }

            int rowsCount = page.path("rows").size();
            if (rowsCount == 0) {
                break;
            }

            for (JsonNode doc : page.path("rows")) {
                processedDocs++;
                Set<String> productsInDoc = extractProductsFromDocument(accountId, token, doc);
                for (String base : productsInDoc) {
                    baseDocsDelta.merge(base, 1L, Long::sum);
                    for (String other : productsInDoc) {
                        if (other.equals(base)) {
                            continue;
                        }
                        String key = base + "|" + other;
                        supportDelta.merge(key, 1L, Long::sum);
                    }
                }
            }

            if (rowsCount < pageSize) {
                break;
            }
            offset += pageSize;
        }

        if (!baseDocsDelta.isEmpty() || !supportDelta.isEmpty()) {
            applyDeltas(accountId, snapshotVersion, baseDocsDelta, supportDelta);
        }

        log.info("Processed {} {} documents for accountId={} baseProductsTouched={} pairDeltas={}",
                processedDocs,
                retail ? "retaildemand" : "demand",
                accountId,
                baseDocsDelta.size(),
                supportDelta.size());

        return !baseDocsDelta.isEmpty() || !supportDelta.isEmpty();
    }

    private Set<String> extractProductsFromDocument(String accountId, String token, JsonNode doc) {
        Set<String> productsInDoc = new HashSet<>();
        JsonNode positions = doc.path("positions").path("rows");
        if (!positions.isArray()) {
            return productsInDoc;
        }

        for (JsonNode pos : positions) {
            String productId = extractProductIdFromAssortment(accountId, token, pos.path("assortment"));
            if (productId != null) {
                productsInDoc.add(productId);
            }
        }
        return productsInDoc;
    }

    private void applyDeltas(String accountId,
                             long snapshotVersion,
                             Map<String, Long> baseDocsDelta,
                             Map<String, Long> supportDelta) {
        deltaJdbcRepository.applyDeltas(accountId, snapshotVersion, baseDocsDelta, supportDelta);
    }

    private long nextSnapshotVersion() {
        return Instant.now().toEpochMilli();
    }

    private String formatMoment(Instant instant) {
        return MOMENT_FORMATTER.format(instant);
    }

    private int resolveLimit(AccountSettings settings, Integer limitOverride) {
        int limit = limitOverride != null ? limitOverride : settings.getLimit();
        return clamp(limit, 1, 100);
    }

    private int resolveMinSupport(AccountSettings settings, Integer minSupportOverride) {
        int minSupport = minSupportOverride != null ? minSupportOverride : settings.getMinSupport();
        return clamp(minSupport, 1, 1000);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String resolveBaseProductId(String accountId, String token, String assortmentId) {
        long now = System.currentTimeMillis();
        String cacheKey = buildBaseProductResolutionCacheKey(accountId, assortmentId);
        CachedBaseProductId cached = baseProductResolutionCache.get(cacheKey);
        if (cached != null && cached.expiresAtMillis() > now) {
            cleanupExpiredBaseProductResolutionEntries(now);
            return cached.productId();
        }
        if (cached != null) {
            baseProductResolutionCache.remove(cacheKey, cached);
        }

        CachedProductInfo productInfo = getCachedProductInfo(accountId, token, assortmentId);
        String resolvedProductId = productInfo != null ? productInfo.id() : null;
        if (resolvedProductId != null && !resolvedProductId.isBlank()) {
            cacheResolvedBaseProductId(cacheKey, resolvedProductId, now);
            return resolvedProductId;
        }

        JsonNode variant = jsonApiClient.get(token, "/entity/variant/" + assortmentId + "?expand=product");
        String variantProductId = variant != null ? extractProductIdFromAssortment(null, null, variant.path("product")) : null;
        String resolved = (variantProductId == null || variantProductId.isBlank()) ? assortmentId : variantProductId;
        cacheResolvedBaseProductId(cacheKey, resolved, now);
        return resolved;
    }

    private String resolveVariantParentProductId(String accountId, String token, String variantId) {
        long now = System.currentTimeMillis();
        String cacheKey = buildBaseProductResolutionCacheKey(accountId, variantId);
        CachedBaseProductId cached = baseProductResolutionCache.get(cacheKey);
        if (cached != null && cached.expiresAtMillis() > now) {
            cleanupExpiredBaseProductResolutionEntries(now);
            return cached.productId();
        }
        if (cached != null) {
            baseProductResolutionCache.remove(cacheKey, cached);
        }

        JsonNode variant = jsonApiClient.get(token, "/entity/variant/" + variantId + "?expand=product");
        String variantProductId = variant != null ? extractProductIdFromAssortment(null, null, variant.path("product")) : null;
        String resolved = (variantProductId == null || variantProductId.isBlank()) ? variantId : variantProductId;
        cacheResolvedBaseProductId(cacheKey, resolved, now);
        return resolved;
    }

    private String extractProductIdFromAssortment(String accountId, String token, JsonNode assortment) {
        if (assortment == null || assortment.isMissingNode() || assortment.isNull()) {
            return null;
        }

        String type = assortment.path("meta").path("type").asText("");
        if ("variant".equals(type)) {
            JsonNode product = assortment.path("product");
            String productId = product.path("id").asText(null);
            if (productId != null && !productId.isBlank()) {
                return productId;
            }

            String productHref = product.path("meta").path("href").asText(null);
            if (productHref != null && !productHref.isBlank()) {
                return extractIdFromMetaHref(productHref);
            }

            String variantId = assortment.path("id").asText(null);
            if ((variantId == null || variantId.isBlank()) && assortment.path("meta").has("href")) {
                variantId = extractIdFromMetaHref(assortment.path("meta").path("href").asText(null));
            }
            if (variantId != null && !variantId.isBlank() && token != null) {
                return resolveVariantParentProductId(accountId, token, variantId);
            }
        }

        String directId = assortment.path("id").asText(null);
        if (directId != null && !directId.isBlank()) {
            return directId;
        }

        String href = assortment.path("meta").path("href").asText(null);
        return href != null ? extractIdFromMetaHref(href) : null;
    }

    private CachedProductInfo getCachedProductInfo(String accountId, String token, String productId) {
        long now = System.currentTimeMillis();
        String cacheKey = buildProductCacheKey(accountId, productId);
        CachedProductInfo cached = productInfoCache.get(cacheKey);
        if (cached != null && cached.expiresAtMillis() > now) {
            cleanupExpiredProductCacheEntries(now);
            return cached;
        }
        if (cached != null) {
            productInfoCache.remove(cacheKey, cached);
        }

        JsonNode product = jsonApiClient.getProductById(token, productId);
        if (product == null) {
            cleanupExpiredProductCacheEntries(now);
            return null;
        }

        CachedProductInfo loaded = new CachedProductInfo(
                product.path("id").asText(null),
                product.path("name").asText(""),
                product.path("article").asText(""),
                product.path("meta").path("uuidHref").asText(""),
                now + PRODUCT_CACHE_TTL_MILLIS
        );
        productInfoCache.put(cacheKey, loaded);
        cleanupExpiredProductCacheEntries(now);
        return loaded;
    }

    private String buildProductCacheKey(String accountId, String productId) {
        String accountSegment = accountId == null ? "_" : accountId;
        return accountSegment + "|" + productId;
    }

    private String buildBaseProductResolutionCacheKey(String accountId, String assortmentId) {
        String accountSegment = accountId == null ? "_" : accountId;
        return accountSegment + "|" + assortmentId;
    }

    private void cleanupExpiredProductCacheEntries(long now) {
        if ((productCacheReads.incrementAndGet() & (PRODUCT_CACHE_CLEANUP_INTERVAL - 1)) != 0) {
            return;
        }
        productInfoCache.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() <= now);
    }

    private void cacheResolvedBaseProductId(String cacheKey, String productId, long now) {
        baseProductResolutionCache.put(cacheKey, new CachedBaseProductId(productId, now + PRODUCT_CACHE_TTL_MILLIS));
        cleanupExpiredBaseProductResolutionEntries(now);
    }

    private void cleanupExpiredBaseProductResolutionEntries(long now) {
        if ((baseProductResolutionReads.incrementAndGet() & (PRODUCT_CACHE_CLEANUP_INTERVAL - 1)) != 0) {
            return;
        }
        baseProductResolutionCache.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() <= now);
    }

    private Instant paddedAnalysisWindowStart(Instant now, int analysisDays) {
        return now.minus(Math.max(1, analysisDays), ChronoUnit.DAYS).minus(1, ChronoUnit.DAYS);
    }

    private Instant paddedAnalysisWindowEnd(Instant now) {
        return now.plus(1, ChronoUnit.DAYS);
    }

    private record CachedProductInfo(String id,
                                     String name,
                                     String article,
                                     String productUrl,
                                     long expiresAtMillis) {
    }

    private record CachedBaseProductId(String productId, long expiresAtMillis) {
    }
}
