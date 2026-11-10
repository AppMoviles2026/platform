package com.collabtech.platform.performance;
import static org.junit.jupiter.api.Assertions.*;
import com.collabtech.platform.performance.domain.model.AttributionModels.*;
import com.collabtech.platform.performance.domain.exceptions.PerformanceFailure;
import com.collabtech.platform.performance.infrastructure.attribution.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
class AttributionDomainTests {
    final Instant now=Instant.parse("2026-01-01T12:00:00Z");
    Mechanism code(){return new Mechanism(UUID.randomUUID(),UUID.randomUUID(),Kind.CODE,"Código",null,null,null,"CP-ABCDEFGH2345",Status.ACTIVE,false,UUID.randomUUID(),now,now,0);}
    @Test void mechanismInvariantsAndVersionedStatusArePreserved(){
        var original=code();var inactive=original.status(0,Status.INACTIVE,now.plusSeconds(1));
        assertEquals(1,inactive.version());assertEquals(original.code(),inactive.code());assertEquals(original.createdAt(),inactive.createdAt());
        assertThrows(PerformanceFailure.class,()->inactive.status(0,Status.ACTIVE,now.plusSeconds(2)));
        assertEquals(1,inactive.status(1,Status.INACTIVE,now.plusSeconds(2)).version());
        assertThrows(IllegalArgumentException.class,()->new Mechanism(UUID.randomUUID(),UUID.randomUUID(),Kind.LINK,"Link","shop","https://example.com",null,null,Status.ACTIVE,false,UUID.randomUUID(),now,now,0));
    }
    @Test void clickSourcesCannotClaimManualBusinessResults(){
        UUID id=UUID.randomUUID();String hash="a".repeat(64);
        assertThrows(IllegalArgumentException.class,()->new Interaction(id,id,id,InteractionType.CODE_REDEMPTION,Source.FIRST_PARTY_TRACKING,hash,hash,null,now,now,null,false));
        assertThrows(IllegalArgumentException.class,()->new Interaction(id,id,id,InteractionType.CLICK,Source.BRAND_DECLARATION,hash,hash,id,now,now,id,false));
        assertThrows(IllegalArgumentException.class,()->new Interaction(id,id,id,InteractionType.INQUIRY,Source.BRAND_DECLARATION,hash,hash,id,now,now,null,false));
        assertThrows(IllegalArgumentException.class,()->new Interaction(id,id,id,InteractionType.CLICK,Source.FIRST_PARTY_TRACKING,hash,hash,null,now.plusSeconds(1),now,null,false));
    }
    @Test void destinationsUseExactServerConfigurationAndPinnedPublicOrigin(){
        var registry=new ConfiguredAttributionDestinations("shop=https://example.com/store?campaign=1","https://collabpro.example/");
        assertEquals("https://example.com/store?campaign=1",registry.resolve("shop").orElseThrow());assertTrue(registry.resolve("https://evil.example").isEmpty());
        assertTrue(registry.publicUrl("token").startsWith("https://collabpro.example/api/v1/public/attribution/"));assertTrue(registry.secureCookie());
        assertTrue(new ConfiguredAttributionDestinations("","http://localhost:8081").available().isEmpty());
        for(String invalid:List.of("shop=http://example.com","shop=https://localhost","shop=https://127.0.0.1","shop=https://user:pass@example.com",
                "shop=https://example.com:8080","shop=https://example.com/#fragment","shop=https://example.com/a/../b","shop=https://example.com,shop=https://other.example"))
            assertThrows(IllegalArgumentException.class,()->new ConfiguredAttributionDestinations(invalid,"https://collabpro.example"),invalid);
        assertThrows(IllegalArgumentException.class,()->new ConfiguredAttributionDestinations("","https://collabpro.example/?target=evil"));
    }
    @Test void identifiersAreOpaqueRandomUniqueAndCodesHaveNoDestination(){
        var identifiers=new RandomAttributionIdentifiers();var tokens=new HashSet<String>();var codes=new HashSet<String>();
        for(int i=0;i<100;i++){String token=identifiers.token(),code=identifiers.code();
            assertTrue(token.matches("[A-Za-z0-9_-]{43}"));assertTrue(code.matches("CP-[A-Z2-9]{12}"));assertTrue(tokens.add(token));assertTrue(codes.add(code));}
    }
}
