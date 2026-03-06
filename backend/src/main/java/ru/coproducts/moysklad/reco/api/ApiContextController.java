package ru.coproducts.moysklad.reco.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.coproducts.moysklad.reco.vendor.VendorContextClient;

import java.time.Instant;
import java.util.Optional;

@RestController
@Validated
@RequestMapping("/api/context")
public class ApiContextController {

    private final VendorContextClient vendorContextClient;
    private final ApiSessionTokenService apiSessionTokenService;

    public ApiContextController(VendorContextClient vendorContextClient,
                                ApiSessionTokenService apiSessionTokenService) {
        this.vendorContextClient = vendorContextClient;
        this.apiSessionTokenService = apiSessionTokenService;
    }

    @PostMapping("/resolve")
    public ResponseEntity<ResolveContextResponse> resolve(@Valid @RequestBody ResolveContextRequest request) {
        Optional<JsonNode> contextOpt = vendorContextClient.fetchContextByKey(request.getContextKey());
        if (contextOpt.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        JsonNode context = contextOpt.get();
        String accountId = extractAccountId(context).orElse(null);
        if (accountId == null || accountId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        ApiSessionTokenService.IssuedSession issued = apiSessionTokenService.issue(accountId);
        ResolveContextResponse response = new ResolveContextResponse(
                issued.token(),
                issued.expiresAt()
        );
        return ResponseEntity.ok(response);
    }

    private Optional<String> extractAccountId(JsonNode context) {
        return textValue(context, "accountId")
                .or(() -> textValue(context, "accountUid"));
    }

    private static Optional<String> textValue(JsonNode node, String key) {
        return Optional.ofNullable(node == null ? null : node.findValue(key))
                .filter(JsonNode::isTextual)
                .map(JsonNode::asText)
                .filter(s -> !s.isBlank());
    }

    public static class ResolveContextRequest {
        @NotBlank
        private String contextKey;

        public String getContextKey() {
            return contextKey;
        }

        public void setContextKey(String contextKey) {
            this.contextKey = contextKey;
        }
    }

    public record ResolveContextResponse(String sessionToken, Instant expiresAt) {
    }
}
