package com.collabtech.platform.identity;

import static org.junit.jupiter.api.Assertions.*;
import com.collabtech.platform.identity.application.projections.IdentityViews;
import com.collabtech.platform.identity.infrastructure.security.DatabaseAccessTokenProvider;
import com.collabtech.platform.identity.infrastructure.security.SecureTokens;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

class JwtSessionSecurityTests {
    private static final String SECRET="8BHwGFufvWLZiSdCxWYzrxBtUMQAJYiWgpDH+aF6BvE=";
    private final Instant now=Instant.parse("2026-10-06T12:00:00Z");
    private final UUID account=UUID.randomUUID();
    private JdbcTemplate jdbc;
    private DatabaseAccessTokenProvider tokens;
    @BeforeEach void setup() {
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:jwt-"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("create table identity_account(account_id varchar(36) primary key,account_type varchar(16),status varchar(16))");
        jdbc.execute("create table identity_access_session(token_hash varchar(64) primary key,account_id varchar(36),expires_at timestamp)");
        jdbc.update("insert into identity_account values(?,'CREATOR','ACTIVE')",account.toString());
        tokens=new DatabaseAccessTokenProvider(jdbc,Clock.fixed(now,ZoneOffset.UTC),SECRET,"issuer","clients",3600);
    }
    @Test void issuesDistinctSignedTokensAndKeepsOnlyHashes() {
        var view=new IdentityViews.AccountView(account,UUID.randomUUID(),"Creador","CREATOR","ACTIVE");
        var first=tokens.issue(view); var second=tokens.issue(view);
        assertNotEquals(first.accessToken(),second.accessToken());
        assertEquals(now.plusSeconds(3600),first.expiresAt());
        assertEquals(account,tokens.authenticate(first.accessToken()).orElseThrow().accountId());
        assertEquals(2,jdbc.queryForObject("select count(*) from identity_access_session",Integer.class));
        assertTrue(jdbc.queryForList("select token_hash from identity_access_session",String.class).stream().allMatch(hash -> hash.length()==64));
    }
    @ParameterizedTest @ValueSource(strings={"issuer","audience","expired","notYetValid","futureIssuedAt","missingExpiry","missingIssuedAt","missingNotBefore","missingId","role","subject"})
    void rejectsInvalidClaimsEvenWhenSignatureAndRegistryAreValid(String variant) {
        var claims=claims();
        switch(variant) {
            case "issuer" -> claims.issuer("foreign");
            case "audience" -> claims.audience(List.of("foreign"));
            case "expired" -> claims.expiresAt(now);
            case "notYetValid" -> claims.notBefore(now.plusSeconds(10));
            case "futureIssuedAt" -> claims.issuedAt(now.plusSeconds(10));
            case "missingExpiry" -> claims.claims(values -> values.remove("exp"));
            case "missingIssuedAt" -> claims.claims(values -> values.remove("iat"));
            case "missingNotBefore" -> claims.claims(values -> values.remove("nbf"));
            case "missingId" -> claims.claims(values -> values.remove("jti"));
            case "role" -> claims.claim("role","BRAND");
            case "subject" -> claims.subject(UUID.randomUUID().toString());
        }
        var raw=sign(claims.build(),SECRET); remember(raw);
        assertTrue(tokens.authenticate(raw).isEmpty());
    }
    @Test void rejectsBadSignatureAlteredPayloadUnsignedAndLegacyTokens() {
        var raw=sign(claims().build(),SECRET); remember(raw);
        var pieces=raw.split("\\.");
        String altered=pieces[0]+"."+Base64.getUrlEncoder().withoutPadding().encodeToString("{\"role\":\"BRAND\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8))+"."+pieces[2];
        var wrongKey=sign(claims().build(),Base64.getEncoder().encodeToString(new byte[32])); remember(wrongKey);
        assertTrue(tokens.authenticate(altered).isEmpty());
        assertTrue(tokens.authenticate(wrongKey).isEmpty());
        assertTrue(tokens.authenticate("eyJhbGciOiJub25lIn0."+pieces[1]+".").isEmpty());
        assertTrue(tokens.authenticate(SecureTokens.random()).isEmpty());
    }
    @Test void signedButUnissuedRevokedInactiveAndExpiredRegistrySessionsAreDenied() {
        var raw=sign(claims().build(),SECRET);
        assertTrue(tokens.authenticate(raw).isEmpty()); remember(raw);
        assertTrue(tokens.authenticate(raw).isPresent());
        jdbc.update("update identity_account set status='SUSPENDED'"); assertTrue(tokens.authenticate(raw).isEmpty());
        jdbc.update("update identity_account set status='ACTIVE'");
        jdbc.update("update identity_access_session set expires_at=?",Timestamp.from(now)); assertTrue(tokens.authenticate(raw).isEmpty());
        jdbc.update("delete from identity_access_session"); assertTrue(tokens.authenticate(raw).isEmpty());
    }
    @Test void rejectsWeakSecretsAndUnsafeConfiguration() {
        assertThrows(IllegalArgumentException.class,()->new DatabaseAccessTokenProvider(jdbc,Clock.systemUTC(),"YWJj","issuer","clients",3600));
        assertThrows(IllegalArgumentException.class,()->new DatabaseAccessTokenProvider(jdbc,Clock.systemUTC(),SECRET,"","clients",3600));
        assertThrows(IllegalArgumentException.class,()->new DatabaseAccessTokenProvider(jdbc,Clock.systemUTC(),SECRET,"issuer","clients",0));
    }
    private JwtClaimsSet.Builder claims() {
        return JwtClaimsSet.builder().issuer("issuer").audience(List.of("clients")).subject(account.toString())
                .issuedAt(now.minusSeconds(5)).notBefore(now.minusSeconds(5)).expiresAt(now.plusSeconds(3600))
                .id(UUID.randomUUID().toString()).claim("role","CREATOR");
    }
    private String sign(JwtClaimsSet claims,String key) {
        var encoder=new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(Base64.getDecoder().decode(key),"HmacSHA256")));
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(),claims)).getTokenValue();
    }
    private void remember(String raw) { jdbc.update("insert into identity_access_session values(?,?,?)",SecureTokens.digest(raw),account.toString(),Timestamp.from(now.plusSeconds(7200))); }
}
