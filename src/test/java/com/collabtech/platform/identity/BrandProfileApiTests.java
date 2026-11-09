package com.collabtech.platform.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import com.collabtech.platform.identity.interfaces.acl.IdentityProfileFacade;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BrandProfileApiTests {
    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired IdentityProfileFacade profiles;
    @LocalServerPort int port;
    MockMvc mvc;
    static final String PATH = "/api/v1/profiles/me/brand";
    static final String PASSWORD = "ProfilePassword123!";
    @BeforeEach void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }

    @Test void initialProfileCanBeSavedAndUpdatedWithoutChangingOtherAccountsOrCredentials() throws Exception {
        var owner = account("brands"); var other = account("brands");
        String hash = jdbc.queryForObject("select password_hash from identity_account where account_id=?", String.class, owner.accountId());
        var initial = read(owner);
        assertEquals(owner.profileId(), initial.path("profileId").asText()); assertEquals(0, initial.path("version").asLong());
        assertNotEquals(owner.accountId(), owner.profileId());
        var saved = save(owner, body(" Ayni Café ", 0), 200);
        assertEquals("Ayni Café", saved.path("businessName").asText()); assertEquals(1, saved.path("version").asLong());
        assertEquals("Lima", read(owner).path("location").asText()); assertEquals("Usuario", read(other).path("businessName").asText());
        var updated = save(owner, body("Ayni nuevo", 1), 200); assertEquals(2, updated.path("version").asLong());
        mvc.perform(get("/api/v1/accounts/me").header("Authorization", "Bearer " + owner.token()))
                .andExpect(jsonPath("$.name").value("Ayni nuevo")).andExpect(jsonPath("$.accountType").value("BRAND"));
        assertEquals(hash, jdbc.queryForObject("select password_hash from identity_account where account_id=?", String.class, owner.accountId()));
        assertEquals("ACTIVE", jdbc.queryForObject("select status from identity_account where account_id=?", String.class, owner.accountId()));
        save(owner, "{\"businessName\":\"Ayni nuevo\",\"expectedVersion\":2}", 200);
        assertTrue(read(owner).path("description").isNull());
    }
    @Test void staleVersionDoesNotOverwriteNewerInformation() throws Exception {
        var owner = account("brands"); save(owner, body("Actual", 0), 200);
        assertEquals("CONCURRENT_UPDATE", save(owner, body("Antigua", 0), 409).path("code").asText());
        assertEquals("Actual", read(owner).path("businessName").asText());
    }
    @ParameterizedTest @ValueSource(strings={"accountId","profileId","email","password","passwordHash","accountType","status"})
    void rejectsReadOnlyFieldsWithoutWriting(String field) throws Exception {
        var owner = account("brands");
        var body = new LinkedHashMap<String,Object>(); body.put("businessName", "Ataque"); body.put("expectedVersion", 0); body.put(field, "forged");
        save(owner, json.writeValueAsString(body), 400); assertEquals("Usuario", read(owner).path("businessName").asText());
    }
    @ParameterizedTest @ValueSource(strings={"missing-version","negative-version","blank-name","long-name","long-description","long-category","long-location"})
    void rejectsInvalidDataAtomically(String mutation) throws Exception {
        var owner = account("brands"); var body = new LinkedHashMap<String,Object>();
        body.put("businessName", "Marca"); body.put("expectedVersion", 0);
        switch (mutation) {
            case "missing-version" -> body.remove("expectedVersion");
            case "negative-version" -> body.put("expectedVersion", -1);
            case "blank-name" -> body.put("businessName", " ");
            case "long-name" -> body.put("businessName", "x".repeat(151));
            case "long-description" -> body.put("description", "x".repeat(2001));
            case "long-category" -> body.put("category", "x".repeat(151));
            case "long-location" -> body.put("location", "x".repeat(151));
        }
        save(owner, json.writeValueAsString(body), 400); assertEquals(0, read(owner).path("version").asLong());
    }
    @Test void endpointsRequireActiveBrandAndCannotAddressAnotherProfile() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        var creator = account("creators"); mvc.perform(get(PATH).header("Authorization", "Bearer " + creator.token())).andExpect(status().isForbidden());
        save(creator, body("Marca", 0), 403);
        var owner = account("brands");
        mvc.perform(get("/api/v1/profiles/" + owner.profileId() + "/brand").header("Authorization", "Bearer " + creator.token())).andExpect(status().isNotFound());
        jdbc.update("update identity_account set status='SUSPENDED' where account_id=?", owner.accountId());
        mvc.perform(get(PATH).header("Authorization", "Bearer " + owner.token())).andExpect(status().isUnauthorized());
    }
    @Test void simultaneousSavesOfTheSameVersionHaveOnlyOneWinner() throws Exception {
        var owner = account("brands"); var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Integer> one = () -> { gate.await(5, TimeUnit.SECONDS); return mvc.perform(put(PATH).header("Authorization", "Bearer " + owner.token())
                    .contentType(MediaType.APPLICATION_JSON).content(body(Thread.currentThread().getName(), 0))).andReturn().getResponse().getStatus(); };
            var a = pool.submit(one); var b = pool.submit(one); gate.countDown();
            var codes = new ArrayList<>(List.of(a.get(15,TimeUnit.SECONDS), b.get(15,TimeUnit.SECONDS))); Collections.sort(codes);
            assertEquals(List.of(200,409), codes); assertEquals(1, read(owner).path("version").asLong());
        }
    }
    @Test void internalContractsUseProfileIdsAndExposeNoSecrets() throws Exception {
        var brand = account("brands"); var creator = account("creators"); save(brand, body("Negocio",0),200);
        assertEquals("Negocio", profiles.brandById(UUID.fromString(brand.profileId())).orElseThrow().businessName());
        assertTrue(profiles.brandById(UUID.fromString(brand.accountId())).isEmpty());
        assertTrue(profiles.creatorById(UUID.fromString(brand.profileId())).isEmpty());
        var details = profiles.creatorById(UUID.fromString(creator.profileId())).orElseThrow();
        assertEquals("Usuario", details.displayName()); assertTrue(details.socialAccounts().isEmpty());
        String serialized = json.writeValueAsString(details);
        assertFalse(serialized.contains("email")); assertFalse(serialized.contains("password")); assertFalse(serialized.contains("token"));
    }
    @Test void socialAccessMetadataIsOwnerOnlyAndNeverReturnsCredentials() throws Exception {
        var owner=account("creators"); var other=account("creators"); var brand=account("brands"); String id=UUID.randomUUID().toString();
        jdbc.update("insert into identity_social_account(social_id,profile_id,platform,external_account_id,username,status) values(?,?,'tiktok',?,'user','ACTIVE')",
                id,owner.profileId(),UUID.randomUUID().toString());
        var result=mvc.perform(get("/api/v1/social-accounts/"+id+"/access").header("Authorization","Bearer "+owner.token()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.status").value("MISSING_CREDENTIALS")).andExpect(jsonPath("$.reauthorizationRequired").value(true))
                .andReturn().getResponse().getContentAsString();
        assertFalse(result.contains("access_token")); assertFalse(result.contains("refresh_token"));
        mvc.perform(get("/api/v1/social-accounts/"+id+"/access").header("Authorization","Bearer "+other.token())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/social-accounts/"+id+"/access").header("Authorization","Bearer "+brand.token())).andExpect(status().isForbidden());
    }
    @Test void realHttpReadsAndUpdatesThePersistedBrandProfile() throws Exception {
        var owner=account("brands"); var client=HttpClient.newHttpClient();
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+PATH)).timeout(Duration.ofSeconds(10))
                .header("Authorization","Bearer "+owner.token()).header("Content-Type","application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body("HTTP",0))).build();
        var response=client.send(request,HttpResponse.BodyHandlers.ofString()); assertEquals(200,response.statusCode());
        assertEquals("HTTP",read(owner).path("businessName").asText());
    }
    private JsonNode read(User user) throws Exception {
        return json.readTree(mvc.perform(get(PATH).header("Authorization","Bearer "+user.token())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private JsonNode save(User user,String body,int expected) throws Exception {
        return json.readTree(mvc.perform(put(PATH).header("Authorization","Bearer "+user.token()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }
    private String body(String name,long version) throws Exception {
        return json.writeValueAsString(Map.of("businessName",name,"description","Café local","category","Food","location","Lima","expectedVersion",version));
    }
    private User account(String route) throws Exception {
        String email="profile-"+UUID.randomUUID()+"@example.com";
        var registration=Map.of(route.equals("brands")?"businessName":"displayName","Usuario","email",email,"password",PASSWORD);
        mvc.perform(post("/api/v1/auth/"+route).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(registration))).andExpect(status().isCreated());
        var session=json.readTree(mvc.perform(post("/api/v1/auth/sessions").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email",email,"password",PASSWORD)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return new User(session.path("account").path("accountId").asText(),session.path("account").path("profileId").asText(),session.path("accessToken").asText());
    }
    record User(String accountId,String profileId,String token) {}
}
