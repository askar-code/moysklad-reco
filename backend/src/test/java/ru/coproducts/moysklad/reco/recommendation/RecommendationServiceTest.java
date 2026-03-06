package ru.coproducts.moysklad.reco.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.coproducts.moysklad.reco.domain.AccountInstallation;
import ru.coproducts.moysklad.reco.domain.AccountInstallationService;
import ru.coproducts.moysklad.reco.domain.AccountSettings;
import ru.coproducts.moysklad.reco.domain.AccountSettingsService;
import ru.coproducts.moysklad.reco.jsonapi.JsonApiClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationServiceTest {

    private final RecommendationPairRepository pairRepository = mock(RecommendationPairRepository.class);
    private final BaseProductStatsRepository baseStatsRepository = mock(BaseProductStatsRepository.class);
    private final AccountInstallationService installationService = mock(AccountInstallationService.class);
    private final AccountSettingsService settingsService = mock(AccountSettingsService.class);
    private final JsonApiClient jsonApiClient = mock(JsonApiClient.class);
    private final RecommendationDeltaJdbcRepository deltaJdbcRepository = mock(RecommendationDeltaJdbcRepository.class);

    private final RecommendationService service =
            new RecommendationService(
                    pairRepository,
                    baseStatsRepository,
                    installationService,
                    settingsService,
                    jsonApiClient,
                    deltaJdbcRepository
            );

    @Test
    void returnsCachedRecommendationsWithProductInfo() {
        String accountId = "acc-1";
        String baseProductId = "base-1";
        String recoProductId = "reco-1";

        RecommendationPair pair = new RecommendationPair();
        pair.setAccountId(accountId);
        pair.setBaseProductId(baseProductId);
        pair.setRecommendedProductId(recoProductId);
        pair.setSupportCount(5);
        pair.setConfidence(0.5);

        when(pairRepository.findByAccountIdAndSnapshotVersionAndBaseProductIdOrderBySupportCountDesc(accountId, 0L, baseProductId))
                .thenReturn(List.of(pair));

        AccountInstallation installation = new AccountInstallation();
        installation.setAccountId(accountId);
        installation.setAccessToken("token");
        when(installationService.findActiveByAccountId(accountId))
                .thenReturn(Optional.of(installation));

        AccountSettings settings = new AccountSettings();
        settings.setAccountId(accountId);
        settings.setAnalysisDays(90);
        settings.setMinSupport(3);
        settings.setLimit(5);
        settings.setActiveRecommendationVersion(0L);
        when(settingsService.getOrDefault(accountId)).thenReturn(settings);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode productNode = mapper.createObjectNode();
        productNode.put("name", "Товар 1");
        productNode.put("article", "A-001");
        productNode.putObject("meta").put("uuidHref", "https://online.moysklad.ru/app/#good/edit?id=legacy-1");
        when(jsonApiClient.getProductById(anyString(), anyString())).thenReturn(productNode);

        // заглушки для запросов к demand/retaildemand не нужны, т.к. в тесте используется уже кэшированное значение

        List<RecommendationDto> result = service.getRecommendations(accountId, baseProductId, null, null);

        assertEquals(1, result.size());
        RecommendationDto dto = result.get(0);
        assertEquals(recoProductId, dto.getProductId());
        assertEquals("Товар 1", dto.getName());
        assertEquals("A-001", dto.getArticle());
        assertEquals("https://online.moysklad.ru/app/#good/edit?id=legacy-1", dto.getProductUrl());
        assertEquals(5, dto.getSupportCount());
    }

    @Test
    void getRecommendationsUsesMinSupportFromAccountSettings() {
        String accountId = "acc-1";
        String baseProductId = "base-1";

        RecommendationPair lowSupport = new RecommendationPair();
        lowSupport.setAccountId(accountId);
        lowSupport.setBaseProductId(baseProductId);
        lowSupport.setRecommendedProductId("reco-low");
        lowSupport.setSupportCount(2);
        lowSupport.setConfidence(0.2);

        RecommendationPair highSupport = new RecommendationPair();
        highSupport.setAccountId(accountId);
        highSupport.setBaseProductId(baseProductId);
        highSupport.setRecommendedProductId("reco-high");
        highSupport.setSupportCount(5);
        highSupport.setConfidence(0.5);

        when(pairRepository.findByAccountIdAndSnapshotVersionAndBaseProductIdOrderBySupportCountDesc(accountId, 0L, baseProductId))
                .thenReturn(List.of(highSupport, lowSupport));

        AccountInstallation installation = new AccountInstallation();
        installation.setAccountId(accountId);
        installation.setAccessToken("token");
        when(installationService.findActiveByAccountId(accountId))
                .thenReturn(Optional.of(installation));

        AccountSettings settings = new AccountSettings();
        settings.setAccountId(accountId);
        settings.setMinSupport(3);
        settings.setLimit(10);
        settings.setActiveRecommendationVersion(0L);
        when(settingsService.getOrDefault(accountId)).thenReturn(settings);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode productNode = mapper.createObjectNode();
        productNode.put("name", "Товар 1");
        productNode.put("article", "A-001");
        productNode.putObject("meta").put("uuidHref", "https://online.moysklad.ru/app/#good/edit?id=legacy-2");
        when(jsonApiClient.getProductById(anyString(), anyString())).thenReturn(productNode);

        List<RecommendationDto> result = service.getRecommendations(accountId, baseProductId, null, null);

        assertEquals(1, result.size());
        assertEquals("reco-high", result.get(0).getProductId());
    }

    @Test
    void getRecommendationsResolvesVariantToParentProduct() {
        String accountId = "acc-1";
        String variantId = "variant-1";
        String productId = "product-1";
        String recoProductId = "reco-1";

        when(pairRepository.findByAccountIdAndSnapshotVersionAndBaseProductIdOrderBySupportCountDesc(accountId, 0L, variantId))
                .thenReturn(List.of());

        RecommendationPair pair = new RecommendationPair();
        pair.setAccountId(accountId);
        pair.setBaseProductId(productId);
        pair.setRecommendedProductId(recoProductId);
        pair.setSupportCount(4);
        pair.setConfidence(0.4);

        when(pairRepository.findByAccountIdAndSnapshotVersionAndBaseProductIdOrderBySupportCountDesc(accountId, 0L, productId))
                .thenReturn(List.of(pair));

        AccountInstallation installation = new AccountInstallation();
        installation.setAccountId(accountId);
        installation.setAccessToken("token");
        when(installationService.findActiveByAccountId(accountId))
                .thenReturn(Optional.of(installation));

        AccountSettings settings = new AccountSettings();
        settings.setAccountId(accountId);
        settings.setMinSupport(1);
        settings.setLimit(10);
        settings.setActiveRecommendationVersion(0L);
        when(settingsService.getOrDefault(accountId)).thenReturn(settings);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode variantNode = mapper.createObjectNode();
        ObjectNode productNode = variantNode.putObject("product");
        productNode.put("id", productId);

        ObjectNode resolvedRecoProduct = mapper.createObjectNode();
        resolvedRecoProduct.put("name", "Рекомендация");
        resolvedRecoProduct.put("article", "R-001");
        resolvedRecoProduct.putObject("meta").put("uuidHref", "https://online.moysklad.ru/app/#good/edit?id=reco-1");

        when(jsonApiClient.getProductById("token", variantId)).thenReturn(null);
        when(jsonApiClient.get("token", "/entity/variant/" + variantId + "?expand=product")).thenReturn(variantNode);
        when(jsonApiClient.getProductById("token", recoProductId)).thenReturn(resolvedRecoProduct);

        List<RecommendationDto> result = service.getRecommendations(accountId, variantId, null, null);

        assertEquals(1, result.size());
        assertEquals(recoProductId, result.get(0).getProductId());
    }

    @Test
    void getRecommendationsCachesProductMetadataBetweenCalls() {
        String accountId = "acc-cache";
        String baseProductId = "base-cache";
        String recoProductId = "reco-cache";

        RecommendationPair pair = new RecommendationPair();
        pair.setAccountId(accountId);
        pair.setBaseProductId(baseProductId);
        pair.setRecommendedProductId(recoProductId);
        pair.setSupportCount(7);
        pair.setConfidence(0.7);

        when(pairRepository.findByAccountIdAndSnapshotVersionAndBaseProductIdOrderBySupportCountDesc(accountId, 0L, baseProductId))
                .thenReturn(List.of(pair));

        AccountInstallation installation = new AccountInstallation();
        installation.setAccountId(accountId);
        installation.setAccessToken("token");
        when(installationService.findActiveByAccountId(accountId))
                .thenReturn(Optional.of(installation));

        AccountSettings settings = new AccountSettings();
        settings.setAccountId(accountId);
        settings.setMinSupport(1);
        settings.setLimit(10);
        settings.setActiveRecommendationVersion(0L);
        when(settingsService.getOrDefault(accountId)).thenReturn(settings);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode productNode = mapper.createObjectNode();
        productNode.put("id", recoProductId);
        productNode.put("name", "Кешируемый товар");
        productNode.put("article", "CACHE-1");
        productNode.putObject("meta").put("uuidHref", "https://online.moysklad.ru/app/#good/edit?id=cache-1");
        when(jsonApiClient.getProductById("token", recoProductId)).thenReturn(productNode);

        List<RecommendationDto> first = service.getRecommendations(accountId, baseProductId, null, null);
        List<RecommendationDto> second = service.getRecommendations(accountId, baseProductId, null, null);

        assertEquals(1, first.size());
        assertEquals(1, second.size());
        verify(jsonApiClient, times(1)).getProductById("token", recoProductId);
    }

    @Test
    void rebuildRecommendationsForAccountUsesBatchDeltaWriter() {
        String accountId = "acc-rebuild";

        AccountInstallation installation = new AccountInstallation();
        installation.setAccountId(accountId);
        installation.setAccessToken("token");
        when(installationService.findActiveByAccountId(accountId))
                .thenReturn(Optional.of(installation));

        AccountSettings settings = new AccountSettings();
        settings.setAccountId(accountId);
        settings.setAnalysisDays(90);
        settings.setActiveRecommendationVersion(0L);
        when(settingsService.getOrDefault(accountId)).thenReturn(settings);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode demands = mapper.createObjectNode();
        demands.putArray("rows").add(makeDocument(mapper, "base-1", "other-1"));

        when(jsonApiClient.fetchDemandsPage(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(demands);
        when(jsonApiClient.fetchRetailDemandsPage(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(null);

        service.rebuildRecommendationsForAccount(accountId);

        verify(deltaJdbcRepository).applyDeltas(eq(accountId), anyLong(), any(), any());
        verify(deltaJdbcRepository).deleteOtherSnapshots(eq(accountId), anyLong());
        assertNotEquals(0L, settings.getActiveRecommendationVersion());
    }

    @Test
    void rebuildRecommendationsForAccountMergesVariantsIntoParentProduct() {
        String accountId = "acc-variants";
        String parentProductId = "product-parent";
        String variantA = "variant-a";
        String variantB = "variant-b";
        String otherProductId = "other-product";

        AccountInstallation installation = new AccountInstallation();
        installation.setAccountId(accountId);
        installation.setAccessToken("token");
        when(installationService.findActiveByAccountId(accountId))
                .thenReturn(Optional.of(installation));

        AccountSettings settings = new AccountSettings();
        settings.setAccountId(accountId);
        settings.setAnalysisDays(90);
        settings.setActiveRecommendationVersion(0L);
        when(settingsService.getOrDefault(accountId)).thenReturn(settings);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode demands = mapper.createObjectNode();
        ArrayNode rows = demands.putArray("rows");
        rows.add(makeDocumentWithAssortments(
                mapper,
                makeVariantAssortment(mapper, variantA),
                makeProductAssortment(mapper, otherProductId)
        ));
        rows.add(makeDocumentWithAssortments(
                mapper,
                makeVariantAssortment(mapper, variantB),
                makeProductAssortment(mapper, otherProductId)
        ));

        when(jsonApiClient.fetchDemandsPage(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(demands);
        when(jsonApiClient.fetchRetailDemandsPage(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(null);
        when(jsonApiClient.get("token", "/entity/variant/" + variantA + "?expand=product"))
                .thenReturn(makeVariantLookup(mapper, parentProductId));
        when(jsonApiClient.get("token", "/entity/variant/" + variantB + "?expand=product"))
                .thenReturn(makeVariantLookup(mapper, parentProductId));

        service.rebuildRecommendationsForAccount(accountId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Long>> baseDocsCaptor = ArgumentCaptor.forClass(Map.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Long>> supportCaptor = ArgumentCaptor.forClass(Map.class);
        verify(deltaJdbcRepository).applyDeltas(eq(accountId), anyLong(), baseDocsCaptor.capture(), supportCaptor.capture());

        Map<String, Long> baseDocsDelta = baseDocsCaptor.getValue();
        Map<String, Long> supportDelta = supportCaptor.getValue();

        assertEquals(2L, baseDocsDelta.get(parentProductId));
        assertEquals(2L, supportDelta.get(parentProductId + "|" + otherProductId));
        verify(jsonApiClient, never()).getProductById("token", variantA);
        verify(jsonApiClient, never()).getProductById("token", variantB);
    }

    @Test
    void rebuildRecommendationsForAccountFailsOnDemandPageError() {
        String accountId = "acc-error";

        AccountInstallation installation = new AccountInstallation();
        installation.setAccountId(accountId);
        installation.setAccessToken("token");
        when(installationService.findActiveByAccountId(accountId))
                .thenReturn(Optional.of(installation));

        AccountSettings settings = new AccountSettings();
        settings.setAccountId(accountId);
        settings.setAnalysisDays(90);
        settings.setActiveRecommendationVersion(0L);
        when(settingsService.getOrDefault(accountId)).thenReturn(settings);

        when(jsonApiClient.fetchDemandsPage(anyString(), anyString(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("boom"));

        assertThrows(RuntimeException.class, () -> service.rebuildRecommendationsForAccount(accountId));
        verify(deltaJdbcRepository, never()).applyDeltas(eq(accountId), anyLong(), any(), any());
        verify(deltaJdbcRepository).deleteSnapshot(eq(accountId), anyLong());
        assertEquals(0L, settings.getActiveRecommendationVersion());
    }

    private ObjectNode makeDocument(ObjectMapper mapper, String... productIds) {
        ObjectNode[] assortments = new ObjectNode[productIds.length];
        for (int i = 0; i < productIds.length; i++) {
            assortments[i] = makeProductAssortment(mapper, productIds[i]);
        }
        return makeDocumentWithAssortments(mapper, assortments);
    }

    private ObjectNode makeDocumentWithAssortments(ObjectMapper mapper, ObjectNode... assortments) {
        ObjectNode doc = mapper.createObjectNode();
        ObjectNode positions = mapper.createObjectNode();
        ArrayNode posRows = positions.putArray("rows");

        for (ObjectNode assortment : assortments) {
            ObjectNode pos = mapper.createObjectNode();
            pos.set("assortment", assortment);
            posRows.add(pos);
        }

        doc.set("positions", positions);
        return doc;
    }

    private ObjectNode makeProductAssortment(ObjectMapper mapper, String productId) {
        ObjectNode assortment = mapper.createObjectNode();
        assortment.put("id", productId);
        ObjectNode meta = assortment.putObject("meta");
        meta.put("type", "product");
        meta.put("href", "https://online.moysklad.ru/api/remap/1.2/entity/product/" + productId);
        return assortment;
    }

    private ObjectNode makeVariantAssortment(ObjectMapper mapper, String variantId) {
        ObjectNode assortment = mapper.createObjectNode();
        assortment.put("id", variantId);
        ObjectNode meta = assortment.putObject("meta");
        meta.put("type", "variant");
        meta.put("href", "https://online.moysklad.ru/api/remap/1.2/entity/variant/" + variantId);
        return assortment;
    }

    private ObjectNode makeVariantLookup(ObjectMapper mapper, String parentProductId) {
        ObjectNode variant = mapper.createObjectNode();
        ObjectNode product = variant.putObject("product");
        product.put("id", parentProductId);
        return variant;
    }
}
