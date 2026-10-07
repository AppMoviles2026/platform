package com.collabtech.platform.campaign.application.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Length-prefixing distinguishes nulls and field boundaries without depending on a JSON mapper. */
public final class RequestFingerprint {
    private RequestFingerprint() {}
    public static String of(String... fields) {
        var canonical = new StringBuilder();
        for (var value : fields) canonical.append(value == null ? "-1:" : value.length() + ":" + value);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
