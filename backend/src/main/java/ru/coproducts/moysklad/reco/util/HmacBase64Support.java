package ru.coproducts.moysklad.reco.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Общие операции для подписи и кодирования JWT (HMAC-SHA256, Base64 URL).
 */
public final class HmacBase64Support {

    private HmacBase64Support() {
    }

    public static byte[] hmacSha256(byte[] data, byte[] key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 failed", e);
        }
    }

    public static String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static byte[] base64UrlDecode(String encoded) {
        return Base64.getUrlDecoder().decode(encoded);
    }

    /**
     * Сравнение подписи в константном времени (защита от timing attack).
     */
    public static boolean constantTimeEquals(byte[] expected, byte[] actual) {
        return expected.length == actual.length && MessageDigest.isEqual(expected, actual);
    }
}
