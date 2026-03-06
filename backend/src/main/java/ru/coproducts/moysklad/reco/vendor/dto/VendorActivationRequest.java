package ru.coproducts.moysklad.reco.vendor.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Модель payload установки/обновления приложения из Vendor API.
 */
public class VendorActivationRequest {

    @NotBlank
    @JsonAlias("appId")
    private String appUid;

    @JsonAlias("accountId")
    private String accountId;

    private String accountName;

    @NotBlank
    private String cause;

    @NotNull
    @NotEmpty
    private List<AccessInfo> access;

    @NotNull
    private SubscriptionInfo subscription;

    public String getAppUid() {
        return appUid;
    }

    public void setAppUid(String appUid) {
        this.appUid = appUid;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getCause() {
        return cause;
    }

    public void setCause(String cause) {
        this.cause = cause;
    }

    public List<AccessInfo> getAccess() {
        return access;
    }

    public void setAccess(List<AccessInfo> access) {
        this.access = access;
    }

    public SubscriptionInfo getSubscription() {
        return subscription;
    }

    public void setSubscription(SubscriptionInfo subscription) {
        this.subscription = subscription;
    }

    public String getAccessToken() {
        if (access == null) {
            return null;
        }
        return access.stream()
                .map(AccessInfo::getAccessToken)
                .filter(token -> token != null && !token.isBlank())
                .findFirst()
                .orElse(null);
    }

    public static class AccessInfo {
        @NotBlank
        private String resource;

        @JsonAlias({"accessToken", "access_token"})
        private String accessToken;

        public String getResource() {
            return resource;
        }

        public void setResource(String resource) {
            this.resource = resource;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }
    }

    public static class SubscriptionInfo {
        @JsonAlias("tariff")
        private String tariffName;

        private String tariffId;

        public String getTariffName() {
            return tariffName;
        }

        public void setTariffName(String tariffName) {
            this.tariffName = tariffName;
        }

        public String getTariffId() {
            return tariffId;
        }

        public void setTariffId(String tariffId) {
            this.tariffId = tariffId;
        }

        public String getTariff() {
            if (tariffName != null && !tariffName.isBlank()) {
                return tariffName;
            }
            return tariffId;
        }
    }
}
