package ru.coproducts.moysklad.reco.recommendation;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.coproducts.moysklad.reco.api.ApiSessionAuthFilter;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping
    public ResponseEntity<List<RecommendationDto>> getRecommendations(HttpServletRequest request,
                                                                      @RequestParam("productId") String productId,
                                                                      @RequestParam(name = "limit", required = false) Integer limit,
                                                                      @RequestParam(name = "minSupport", required = false) Integer minSupport) {
        String accountId = resolveAccountId(request);
        var result = recommendationService.getRecommendations(accountId, productId, limit, minSupport);
        return ResponseEntity.ok(result);
    }

    private String resolveAccountId(HttpServletRequest request) {
        Object accountId = request.getAttribute(ApiSessionAuthFilter.ACCOUNT_ID_ATTR);
        if (accountId instanceof String value && !value.isBlank()) {
            return value;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing API session");
    }
}
