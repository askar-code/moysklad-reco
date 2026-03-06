package ru.coproducts.moysklad.reco.settings;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.coproducts.moysklad.reco.api.ApiSessionAuthFilter;
import ru.coproducts.moysklad.reco.domain.AccountSettings;
import ru.coproducts.moysklad.reco.domain.AccountSettingsService;
import ru.coproducts.moysklad.reco.recommendation.RecommendationRebuildCoordinator;

@RestController
@RequestMapping("/api/settings")
public class AccountSettingsController {

    private final AccountSettingsService settingsService;
    private final RecommendationRebuildCoordinator rebuildCoordinator;

    public AccountSettingsController(AccountSettingsService settingsService,
                                     RecommendationRebuildCoordinator rebuildCoordinator) {
        this.settingsService = settingsService;
        this.rebuildCoordinator = rebuildCoordinator;
    }

    @GetMapping
    public ResponseEntity<AccountSettingsDto> getSettings(HttpServletRequest request) {
        String accountId = resolveAccountId(request);
        AccountSettings settings = settingsService.getOrDefault(accountId);
        AccountSettingsDto dto = toDto(settings);
        return ResponseEntity.ok(dto);
    }

    @PostMapping
    public ResponseEntity<AccountSettingsDto> saveSettings(HttpServletRequest request,
                                                           @Valid @RequestBody AccountSettingsDto dto) {
        String accountId = resolveAccountId(request);
        AccountSettings saved = settingsService.save(
                accountId,
                dto.getAnalysisDays(),
                dto.getMinSupport(),
                dto.getLimit()
        );
        return ResponseEntity.ok(toDto(saved));
    }

    @GetMapping("/rebuild-status")
    public ResponseEntity<RebuildStatusDto> getRebuildStatus(HttpServletRequest request) {
        String accountId = resolveAccountId(request);
        return ResponseEntity.ok(toRebuildStatusDto(rebuildCoordinator.getStatus(accountId)));
    }

    @PostMapping("/rebuild")
    public ResponseEntity<RebuildStatusDto> triggerRebuild(HttpServletRequest request) {
        String accountId = resolveAccountId(request);
        RecommendationRebuildCoordinator.RebuildStatus status = rebuildCoordinator.requestManualRebuild(accountId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(toRebuildStatusDto(status));
    }

    private AccountSettingsDto toDto(AccountSettings settings) {
        AccountSettingsDto dto = new AccountSettingsDto();
        dto.setAnalysisDays(settings.getAnalysisDays());
        dto.setMinSupport(settings.getMinSupport());
        dto.setLimit(settings.getLimit());
        dto.setRebuildInProgress(settings.isRebuildInProgress());
        dto.setRebuildStartedAt(settings.getRebuildStartedAt());
        dto.setRebuildFinishedAt(settings.getRebuildFinishedAt());
        return dto;
    }

    private RebuildStatusDto toRebuildStatusDto(RecommendationRebuildCoordinator.RebuildStatus status) {
        return new RebuildStatusDto(status.inProgress(), status.startedAt(), status.finishedAt());
    }

    private String resolveAccountId(HttpServletRequest request) {
        Object accountId = request.getAttribute(ApiSessionAuthFilter.ACCOUNT_ID_ATTR);
        if (accountId instanceof String value && !value.isBlank()) {
            return value;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing API session");
    }

    public record RebuildStatusDto(boolean rebuildInProgress,
                                   java.time.Instant rebuildStartedAt,
                                   java.time.Instant rebuildFinishedAt) {
    }
}
