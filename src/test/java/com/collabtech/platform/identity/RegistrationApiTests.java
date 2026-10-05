package com.collabtech.platform.identity;

import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.collabtech.platform.identity.application.ports.PasswordHasher;
import com.collabtech.platform.identity.domain.exceptions.DuplicateEmailException;
import com.collabtech.platform.identity.domain.model.aggregates.Account;
import com.collabtech.platform.identity.domain.model.valueobjects.EmailAddress;
import com.collabtech.platform.identity.domain.repositories.AccountRepository;
import java.time.Instant;
import java.time.Duration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

/** Real handlers, hashing, migration and JPA adapter. Can also run against isolated Docker MySQL. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RegistrationApiTests {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordHasher passwords;
    @Autowired AccountRepository accounts;
    private MockMvc mvc;
    @LocalServerPort int port;
    private static final String PASSWORD = "MiClaveDePrueba123!";

    @BeforeEach void setup() { mvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build(); }

    @ParameterizedTest
    @CsvSource({"brands,BRAND,identity_brand_profile,business_name", "creators,CREATOR,identity_creator_profile,display_name"})
    void validRegistrationPersistsExactlyOneMatchingProfile(String route, String type, String table, String column) throws Exception {
        String email = email();
        mvc.perform(post("/api/v1/auth/" + route).contentType(MediaType.APPLICATION_JSON)
                        .content(payload(route, "  Mi nombre  ", "  " + email.toUpperCase() + "  ", PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountType").value(type))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.name").value("Mi nombre"))
                .andExpect(jsonPath("$.accountId", matchesPattern("[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.profileId", matchesPattern("[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
        var loaded = accounts.findByEmail(new EmailAddress(email)).orElseThrow();
        assertEquals(email, loaded.email().value());
        assertNotEquals(PASSWORD, loaded.passwordHash());
        assertTrue(passwords.matches(PASSWORD, loaded.passwordHash()));
        assertEquals("Mi nombre", jdbc.queryForObject("select " + column + " from " + table + " where account_id = ?",
                String.class, loaded.id().value().toString()));
        String opposite = route.equals("brands") ? "identity_creator_profile" : "identity_brand_profile";
        assertEquals(0, jdbc.queryForObject("select count(*) from " + opposite + " where account_id = ?",
                Integer.class, loaded.id().value().toString()));
        assertEquals(loaded.id(), accounts.findById(loaded.id()).orElseThrow().id());
    }

    @ParameterizedTest
    @CsvSource({"brands,brands", "creators,creators", "brands,creators", "creators,brands"})
    void duplicateEmailIsRejectedAcrossAllRoles(String first, String second) throws Exception {
        String email = email();
        mvc.perform(post("/api/v1/auth/" + first).contentType(MediaType.APPLICATION_JSON)
                .content(payload(first, "Primero", email, PASSWORD))).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/auth/" + second).contentType(MediaType.APPLICATION_JSON)
                        .content(payload(second, "Duplicado", email.toUpperCase(), PASSWORD)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.fieldErrors.email").exists());
        assertEquals(1, jdbc.queryForObject("select count(*) from identity_account where email = ?", Integer.class, email));
    }

    @ParameterizedTest @ValueSource(strings = {"brands", "creators"})
    void incompleteRegistrationDoesNotWriteAnything(String route) throws Exception {
        String email = email();
        mvc.perform(post("/api/v1/auth/" + route).contentType(MediaType.APPLICATION_JSON)
                        .content(payload(route, " ", email, "short")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.password").exists());
        assertFalse(accounts.existsByEmail(new EmailAddress(email)));
    }

    @ParameterizedTest @ValueSource(strings = {"brands", "creators"})
    void invalidEmailReturnsFieldError(String route) throws Exception {
        mvc.perform(post("/api/v1/auth/" + route).contentType(MediaType.APPLICATION_JSON)
                        .content(payload(route, "Nombre", "correo-invalido", PASSWORD)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @ParameterizedTest @ValueSource(strings = {"brands", "creators"})
    void roleCannotBeInjectedIntoTheRequest(String route) throws Exception {
        String email = email();
        String json = payload(route, "Nombre", email, PASSWORD);
        json = json.substring(0, json.length() - 1) + ",\"accountType\":\"ADMIN\"}";
        mvc.perform(post("/api/v1/auth/" + route).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
        assertFalse(accounts.existsByEmail(new EmailAddress(email)));
    }

    @Test void malformedBodyDoesNotExposeSubmittedPassword() throws Exception {
        var result = mvc.perform(post("/api/v1/auth/brands").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + PASSWORD + "\""))
                .andExpect(status().isBadRequest()).andReturn();
        assertFalse(result.getResponse().getContentAsString().contains(PASSWORD));
    }

    @Test void landingPresentationEndpointsHaveBeenRemoved() throws Exception {
        for (String route : List.of("brands", "creators")) {
            mvc.perform(get("/api/v1/public/presentations/" + route)).andExpect(status().isNotFound());
        }
    }

    @Test void bothRegistrationRoutesWorkOverRealHttp() throws Exception {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        for (String route : List.of("brands", "creators")) {
            String email = email();
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/" + route))
                    .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload(route, "Prueba HTTP", email, PASSWORD))).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(201, response.statusCode(), response.body());
            assertTrue(accounts.existsByEmail(new EmailAddress(email)));
            assertFalse(response.body().contains(PASSWORD));
        }
    }

    @Test void databaseRejectsConcurrentDuplicateAfterBothPrechecks() throws Exception {
        var email = new EmailAddress(email());
        String hash = passwords.hash(PASSWORD);
        var barrier = new CyclicBarrier(2);
        Callable<Boolean> brand = () -> saveAfterBarrier(Account.registerBrand(email, hash, "Empresa", Instant.now()), barrier);
        Callable<Boolean> creator = () -> saveAfterBarrier(Account.registerCreator(email, hash, "Creador", Instant.now()), barrier);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var results = executor.invokeAll(List.of(brand, creator), 20, TimeUnit.SECONDS);
            int successes = 0;
            for (var result : results) if (result.get()) successes++;
            assertEquals(1, successes);
        } finally { executor.shutdownNow(); }
        assertEquals(1, jdbc.queryForObject("select count(*) from identity_account where email = ?", Integer.class, email.value()));
        assertEquals(1, jdbc.queryForObject("select count(*) from identity_brand_profile b join identity_account a on a.account_id=b.account_id where a.email=?",
                Integer.class, email.value()) + jdbc.queryForObject("select count(*) from identity_creator_profile c join identity_account a on a.account_id=c.account_id where a.email=?",
                Integer.class, email.value()));
    }

    @Test void profilePersistenceFailureRollsBackTheAccountInsert() {
        var email = new EmailAddress(email());
        var profile = new com.collabtech.platform.identity.domain.model.entities.BrandProfile(
                new com.collabtech.platform.identity.domain.model.valueobjects.BrandProfileId(UUID.randomUUID()),
                "Empresa", "x".repeat(2001), null, null);
        var account = new Account(new com.collabtech.platform.identity.domain.model.valueobjects.AccountId(UUID.randomUUID()),
                email, passwords.hash(PASSWORD), com.collabtech.platform.identity.domain.model.valueobjects.AccountType.BRAND,
                com.collabtech.platform.identity.domain.model.valueobjects.AccountStatus.ACTIVE, Instant.now(), profile, null);
        assertThrows(RuntimeException.class, () -> accounts.save(account));
        assertFalse(accounts.existsByEmail(email));
    }

    private boolean saveAfterBarrier(Account account, CyclicBarrier barrier) throws Exception {
        assertFalse(accounts.existsByEmail(account.email()));
        barrier.await(10, TimeUnit.SECONDS);
        try { accounts.save(account); return true; }
        catch (DuplicateEmailException expected) { return false; }
    }

    private static String email() { return "test-" + UUID.randomUUID() + "@example.com"; }
    private static String payload(String route, String name, String email, String password) {
        return "{\"" + (route.equals("brands") ? "businessName" : "displayName") + "\":\"" + name
                + "\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }
}
