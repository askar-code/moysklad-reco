package ru.coproducts.moysklad.reco.vendor.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.coproducts.moysklad.reco.domain.AccountInstallation;
import ru.coproducts.moysklad.reco.domain.AccountInstallationService;
import ru.coproducts.moysklad.reco.vendor.dto.VendorActivationRequest;

import java.util.HashMap;
import java.util.Map;

@RestController
public class VendorController {

    private static final Logger log = LoggerFactory.getLogger(VendorController.class);

    private final AccountInstallationService accountInstallationService;

    public VendorController(AccountInstallationService accountInstallationService) {
        this.accountInstallationService = accountInstallationService;
    }

    @PutMapping("/api/moysklad/vendor/1.0/apps/{appUid}/{accountId}")
    public ResponseEntity<Map<String, Object>> installOrUpdate(@PathVariable("appUid") String appUid,
                                                               @PathVariable("accountId") String accountId,
                                                               @Valid @RequestBody VendorActivationRequest request) {
        return activateInternal(appUid, accountId, request);
    }

    @DeleteMapping("/api/moysklad/vendor/1.0/apps/{appUid}/{accountId}")
    public ResponseEntity<Map<String, Object>> uninstall(@PathVariable("appUid") String appUid,
                                                         @PathVariable("accountId") String accountId) {
        log.info("Deactivating account installation from Vendor API appUid={} accountId={}", appUid, accountId);
        accountInstallationService.deactivate(accountId);

        Map<String, Object> body = new HashMap<>();
        body.put("status", "Deactivated");
        body.put("accountId", accountId);
        return ResponseEntity.ok(body);
    }

    private ResponseEntity<Map<String, Object>> activateInternal(String appUid,
                                                                 String accountId,
                                                                 VendorActivationRequest request) {
        AccountInstallation installation = accountInstallationService.activate(accountId, request);

        Map<String, Object> body = new HashMap<>();
        body.put("status", "Activated");
        body.put("accountId", installation.getAccountId());
        body.put("tariff", installation.getTariff());
        body.put("appUid", appUid);

        return ResponseEntity.ok(body);
    }
}
