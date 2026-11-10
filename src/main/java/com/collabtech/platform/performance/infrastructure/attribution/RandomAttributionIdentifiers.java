package com.collabtech.platform.performance.infrastructure.attribution;
import com.collabtech.platform.performance.application.ports.AttributionPorts.Identifiers;
import java.security.SecureRandom;
import java.util.Base64;
public final class RandomAttributionIdentifiers implements Identifiers {
    private final SecureRandom random=new SecureRandom();
    public String token(){byte[] bytes=new byte[32];random.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    public String code(){String alphabet="ABCDEFGHJKLMNPQRSTUVWXYZ23456789";var value=new StringBuilder("CP-");
        for(int i=0;i<12;i++)value.append(alphabet.charAt(random.nextInt(alphabet.length())));return value.toString();}
}
