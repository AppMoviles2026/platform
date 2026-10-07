package com.collabtech.platform.campaign;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import com.collabtech.platform.campaign.domain.model.valueobjects.*;
import com.collabtech.platform.campaign.domain.model.entities.*;
import com.collabtech.platform.campaign.domain.repositories.CampaignRepository;
import com.collabtech.platform.campaign.application.ports.CampaignUnitOfWork;
import java.util.UUID;
import java.util.List;
import java.time.Instant;
import java.sql.Timestamp;
import java.math.BigDecimal;
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
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CampaignPreparationApiTests {
    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired CampaignRepository campaigns;
    @Autowired CampaignUnitOfWork transactions;
    @LocalServerPort int port;
    private MockMvc mvc;
    private static final String METADATA = "{\"title\":\"Campaña de prueba\",\"objective\":\"Mostrar producto\",\"category\":\"Moda\",\"targetAudience\":\"Adultos de Lima\",\"location\":\"Lima\"}";
    @BeforeEach void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }
    @Test void onlyOwnerCanDiscardDraftIncludingConditionsButCannotDeletePublishedCampaign() throws Exception {
        var brand=account("brands"); var other=account("brands"); var creator=account("creators"); String id=create(brand.token);
        conditions(brand.token,id,validConditions()).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/campaigns/"+id).header("Authorization",bearer(other.token))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/campaigns/"+id).header("Authorization",bearer(creator.token))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/campaigns/"+id).header("Authorization",bearer(brand.token))).andExpect(status().isNoContent());
        assertTrue(campaigns.findById(new CampaignId(UUID.fromString(id))).isEmpty());
        mvc.perform(delete("/api/v1/campaigns/"+id).header("Authorization",bearer(brand.token))).andExpect(status().isNotFound());
        String published=create(brand.token); conditions(brand.token,published,validConditions()).andExpect(status().isOk());
        mvc.perform(post("/api/v1/campaigns/"+published+"/publication").header("Authorization",bearer(brand.token))).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/campaigns/"+published).header("Authorization",bearer(brand.token)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CAMPAIGN_NOT_DRAFT"));
    }
    @Test void campaignCreationReplaysResponseAndRejectsKeyReuseWithDifferentData() throws Exception {
        var brand=account("brands"); String key=UUID.randomUUID().toString();
        var first=mvc.perform(post("/api/v1/campaigns").header("Authorization",bearer(brand.token)).header("Idempotency-Key",key)
                .contentType(MediaType.APPLICATION_JSON).content(METADATA)).andExpect(status().isCreated()).andReturn().getResponse();
        var replay=mvc.perform(post("/api/v1/campaigns").header("Authorization",bearer(brand.token)).header("Idempotency-Key",key)
                .contentType(MediaType.APPLICATION_JSON).content(METADATA)).andExpect(status().isCreated()).andReturn().getResponse();
        assertEquals(first.getContentAsString(),replay.getContentAsString()); assertEquals(first.getHeader("Location"),replay.getHeader("Location"));
        assertEquals(1,jdbc.queryForObject("select count(*) from campaign_campaign where brand_id=?",Integer.class,brand.profileId));
        mvc.perform(post("/api/v1/campaigns").header("Authorization",bearer(brand.token)).header("Idempotency-Key",key)
                .contentType(MediaType.APPLICATION_JSON).content(METADATA.replace("Campaña de prueba","Otra campaña")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        mvc.perform(post("/api/v1/campaigns").header("Authorization",bearer(brand.token)).header("Idempotency-Key","short")
                .contentType(MediaType.APPLICATION_JSON).content(METADATA)).andExpect(status().isBadRequest());
        var other=account("brands");
        var isolated=mvc.perform(post("/api/v1/campaigns").header("Authorization",bearer(other.token)).header("Idempotency-Key",key)
                .contentType(MediaType.APPLICATION_JSON).content(METADATA)).andExpect(status().isCreated()).andReturn().getResponse();
        assertNotEquals(first.getHeader("Location"),isolated.getHeader("Location"));
    }
    @Test void concurrentCampaignCreationWithSameKeyCreatesOnlyOneDraft() throws Exception {
        var brand=account("brands"); String key=UUID.randomUUID().toString();
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2); var start=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Callable<String> action=() -> {
            start.await();
            return mvc.perform(post("/api/v1/campaigns").header("Authorization",bearer(brand.token)).header("Idempotency-Key",key)
                    .contentType(MediaType.APPLICATION_JSON).content(METADATA)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        };
        try {
            var first=pool.submit(action); var second=pool.submit(action); start.countDown();
            assertEquals(first.get(20,java.util.concurrent.TimeUnit.SECONDS),second.get(20,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1,jdbc.queryForObject("select count(*) from campaign_campaign where brand_id=?",Integer.class,brand.profileId));
        } finally { start.countDown(); pool.shutdownNow(); }
    }
    @Test void completeCampaignIsPublishedAndVisibleToEveryRegisteredCreator() throws Exception {
        var brand = account("brands"); var first = account("creators"); var second = account("creators");
        String id = create(brand.token);
        mvc.perform(get("/api/v1/campaigns/" + id).header("Authorization", bearer(brand.token)))
                .andExpect(jsonPath("$.status").value("DRAFT")).andExpect(jsonPath("$.brandId").value(brand.profileId))
                .andExpect(jsonPath("$.requirements.length()").value(0));
        assertFalse(published(first.token).contains(id));
        conditions(brand.token, id, validConditions()).andExpect(status().isOk()).andExpect(jsonPath("$.compensation.amount").value(500.0));
        mvc.perform(post("/api/v1/campaigns/" + id + "/publication").header("Authorization", bearer(brand.token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.publicationDate").isNotEmpty()).andExpect(jsonPath("$.acceptsApplications").value(true));
        assertTrue(published(first.token).contains(id)); assertTrue(published(second.token).contains(id));
        assertEquals("OPEN", jdbc.queryForObject("select status from campaign_campaign where campaign_id=?", String.class, id));
    }
    @Test void incompleteCampaignCannotBePublishedAndStaysPrivate() throws Exception {
        var brand = account("brands"); var creator = account("creators"); String id = create(brand.token);
        mvc.perform(post("/api/v1/campaigns/" + id + "/publication").header("Authorization", bearer(brand.token)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INCOMPLETE_CAMPAIGN"));
        assertEquals("DRAFT", jdbc.queryForObject("select status from campaign_campaign where campaign_id=?", String.class, id));
        assertFalse(published(creator.token).contains(id));
    }
    @Test void conditionsCanBeSavedAndReplacedAtomicallyBeforePublication() throws Exception {
        var brand = account("brands"); String id = create(brand.token);
        conditions(brand.token, id, validConditions()).andExpect(status().isOk());
        conditions(brand.token, id, validConditions().replace("Afinidad con moda", "Experiencia con moda")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/campaigns/" + id).header("Authorization", bearer(brand.token)))
                .andExpect(jsonPath("$.requirements[0].description").value("Experiencia con moda"))
                .andExpect(jsonPath("$.deliverables[0].quantity").value(1));
        assertEquals(1, jdbc.queryForObject("select count(*) from campaign_requirement where campaign_id=?", Integer.class, id));
        assertEquals(1, jdbc.queryForObject("select count(*) from campaign_deliverable_specification where campaign_id=?", Integer.class, id));
    }
    @Test void incompatibleDatesDoNotReplacePreviouslyValidConditions() throws Exception {
        var brand = account("brands"); String id = create(brand.token);
        conditions(brand.token, id, validConditions()).andExpect(status().isOk());
        String before = jdbc.queryForObject("select requirement_id from campaign_requirement where campaign_id=?", String.class, id);
        String invalid = json.readTree(validConditions()).path("applicationDeadline").asText();
        var body = json.readTree(validConditions()).deepCopy();
        ((tools.jackson.databind.node.ObjectNode) body.path("deliverables").path(0)).put("deadline", invalid);
        conditions(brand.token, id, body.toString()).andExpect(status().isUnprocessableEntity());
        assertEquals(before, jdbc.queryForObject("select requirement_id from campaign_requirement where campaign_id=?", String.class, id));
        var expired = (tools.jackson.databind.node.ObjectNode) json.readTree(validConditions());
        expired.put("applicationDeadline", Instant.now().minusSeconds(1).toString());
        conditions(brand.token, id, expired.toString()).andExpect(status().isUnprocessableEntity());
    }
    @ParameterizedTest @ValueSource(strings={"zeroQuantity","emptyRequirements","emptyDeliverables","cashNoAmount","cashBadCurrency","barterWithAmount","blankRequirement","nullElement"})
    void invalidConditionPayloadsAreRejectedWithoutPartialWrites(String mutation) throws Exception {
        var brand = account("brands"); String id = create(brand.token);
        var body = (tools.jackson.databind.node.ObjectNode) json.readTree(validConditions());
        switch (mutation) {
            case "zeroQuantity" -> ((tools.jackson.databind.node.ObjectNode) body.path("deliverables").path(0)).put("quantity", 0);
            case "emptyRequirements" -> body.putArray("requirements");
            case "emptyDeliverables" -> body.putArray("deliverables");
            case "cashNoAmount" -> ((tools.jackson.databind.node.ObjectNode) body.path("compensation")).remove("amount");
            case "cashBadCurrency" -> ((tools.jackson.databind.node.ObjectNode) body.path("compensation")).put("currency", "XYZ");
            case "barterWithAmount" -> ((tools.jackson.databind.node.ObjectNode) body.path("compensation")).put("type", "BARTER");
            case "blankRequirement" -> ((tools.jackson.databind.node.ObjectNode) body.path("requirements").path(0)).put("description", " ");
            case "nullElement" -> ((tools.jackson.databind.node.ArrayNode) body.path("requirements")).addNull();
        }
        conditions(brand.token, id, body.toString()).andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("select count(*) from campaign_requirement where campaign_id=?", Integer.class, id));
        assertNull(jdbc.queryForObject("select compensation_type from campaign_campaign where campaign_id=?", String.class, id));
    }
    @Test void barterCanBePublishedWithoutBillingOperations() throws Exception {
        var brand = account("brands"); String id = create(brand.token);
        var body = (tools.jackson.databind.node.ObjectNode) json.readTree(validConditions());
        var compensation = (tools.jackson.databind.node.ObjectNode) body.path("compensation");
        compensation.put("type", "PRODUCT"); compensation.remove("amount"); compensation.remove("currency");
        compensation.put("description", "Dos productos de la marca");
        conditions(brand.token, id, body.toString()).andExpect(status().isOk()).andExpect(jsonPath("$.compensation.type").value("PRODUCT"));
        mvc.perform(post("/api/v1/campaigns/" + id + "/publication").header("Authorization", bearer(brand.token))).andExpect(status().isOk());
    }
    @Test void anotherBrandCannotReadChangeOrPublishCampaign() throws Exception {
        var owner = account("brands"); var other = account("brands"); String id = create(owner.token);
        mvc.perform(get("/api/v1/campaigns/" + id).header("Authorization", bearer(other.token))).andExpect(status().isForbidden());
        conditions(other.token, id, validConditions()).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/campaigns/" + id + "/publication").header("Authorization", bearer(other.token))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/campaigns/mine").header("Authorization", bearer(other.token))).andExpect(jsonPath("$.total").value(0));
    }
    @Test void creatorsAndAnonymousAccountsCannotPrepareCampaigns() throws Exception {
        var creator = account("creators");
        mvc.perform(post("/api/v1/campaigns").contentType(MediaType.APPLICATION_JSON).content(METADATA)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/campaigns").header("Authorization", bearer(creator.token)).contentType(MediaType.APPLICATION_JSON).content(METADATA)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/campaigns/mine").header("Authorization", bearer(creator.token))).andExpect(status().isForbidden());
    }
    @Test void suspendedBrandCannotUseExistingSession() throws Exception {
        var brand = account("brands");
        jdbc.update("update identity_account set status='SUSPENDED' where account_id=?", brand.accountId);
        mvc.perform(post("/api/v1/campaigns").header("Authorization", bearer(brand.token)).contentType(MediaType.APPLICATION_JSON).content(METADATA)).andExpect(status().isUnauthorized());
    }
    @Test void invalidMetadataAndForgedOwnershipAreRejected() throws Exception {
        var brand = account("brands");
        mvc.perform(post("/api/v1/campaigns").header("Authorization", bearer(brand.token)).contentType(MediaType.APPLICATION_JSON).content(METADATA.replace("Campaña de prueba", " "))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/campaigns").header("Authorization", bearer(brand.token)).contentType(MediaType.APPLICATION_JSON).content(METADATA.replace("Adultos de Lima", " "))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/campaigns").header("Authorization", bearer(brand.token)).contentType(MediaType.APPLICATION_JSON).content(METADATA.replace("{", "{\"brandId\":\"" + UUID.randomUUID() + "\","))).andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("select count(*) from campaign_campaign where brand_id=?", Integer.class, brand.profileId));
    }
    @Test void publishedConditionsAreFrozenAndRepublicationIsRejected() throws Exception {
        var brand = account("brands"); String id = create(brand.token); conditions(brand.token,id,validConditions()).andExpect(status().isOk());
        mvc.perform(post("/api/v1/campaigns/"+id+"/publication").header("Authorization",bearer(brand.token))).andExpect(status().isOk());
        conditions(brand.token,id,validConditions()).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/campaigns/"+id+"/publication").header("Authorization",bearer(brand.token))).andExpect(status().isConflict());
    }
    @Test void deadlinesAreRecheckedAtPublishTime() throws Exception {
        var brand = account("brands"); String id = create(brand.token); conditions(brand.token,id,validConditions()).andExpect(status().isOk());
        jdbc.update("update campaign_campaign set application_deadline=? where campaign_id=?", Timestamp.from(Instant.now().minusSeconds(1)), id);
        mvc.perform(post("/api/v1/campaigns/"+id+"/publication").header("Authorization",bearer(brand.token))).andExpect(status().isUnprocessableEntity());
    }
    @Test void futureDatesBeyondTimestamp2038LimitRemainValid() throws Exception {
        var brand = account("brands"); String id = create(brand.token);
        var body = (tools.jackson.databind.node.ObjectNode) json.readTree(validConditions());
        body.put("applicationDeadline", "2060-01-01T12:00:00Z");
        ((tools.jackson.databind.node.ObjectNode) body.path("deliverables").path(0)).put("deadline", "2060-02-01T12:00:00Z");
        conditions(brand.token,id,body.toString()).andExpect(status().isOk()).andExpect(jsonPath("$.applicationDeadline").value("2060-01-01T12:00:00Z"));
        mvc.perform(get("/api/v1/campaigns/"+id).header("Authorization",bearer(brand.token))).andExpect(jsonPath("$.deliverables[0].deadline").value("2060-02-01T12:00:00Z"));
    }
    @Test void pagingUnknownIdsAndPrivateDraftsHaveSafeResponses() throws Exception {
        var brand = account("brands"); var creator = account("creators"); String id = create(brand.token);
        mvc.perform(get("/api/v1/campaigns/mine?page=0&size=1").header("Authorization",bearer(brand.token))).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].id").value(id));
        mvc.perform(get("/api/v1/campaigns/mine?page=-1").header("Authorization",bearer(brand.token))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/campaigns/published?size=101").header("Authorization",bearer(creator.token))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/campaigns/"+UUID.randomUUID()).header("Authorization",bearer(brand.token))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/campaigns/not-a-uuid").header("Authorization",bearer(brand.token))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/campaigns/"+id).header("Authorization",bearer(creator.token))).andExpect(status().isForbidden());
    }
    @Test void staleAggregateAndTransactionFailureDoNotOverwriteConditions() throws Exception {
        var brand = account("brands"); var id = new CampaignId(UUID.fromString(create(brand.token)));
        var first = campaigns.findById(id).orElseThrow(); var stale = campaigns.findById(id).orElseThrow();
        define(first,"Primera condición"); campaigns.save(first);
        define(stale,"Cambio obsoleto"); assertThrows(org.springframework.orm.ObjectOptimisticLockingFailureException.class, () -> campaigns.save(stale));
        assertEquals("Primera condición", campaigns.findById(id).orElseThrow().requirements().get(0).description());
        assertThrows(IllegalStateException.class, () -> transactions.execute(() -> {
            var latest = campaigns.findById(id).orElseThrow(); define(latest,"Rollback"); campaigns.save(latest); throw new IllegalStateException("Failure after saving");
        }));
        assertEquals("Primera condición", campaigns.findById(id).orElseThrow().requirements().get(0).description());
    }
    @Test void realHttpUsesSecurityAndReturnsPersistedDraft() throws Exception {
        var brand = account("brands");
        var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:"+port+"/api/v1/campaigns"))
                .header("Authorization",bearer(brand.token)).header("Content-Type","application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(METADATA)).build();
        var result = java.net.http.HttpClient.newHttpClient().send(request,java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(201,result.statusCode()); assertEquals("DRAFT",json.readTree(result.body()).path("status").asText());
    }
    @Test void simultaneousUpdatesNeverLoseDataSilently() throws Exception {
        var brand = account("brands"); var id = new CampaignId(UUID.fromString(create(brand.token)));
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var ready = new java.util.concurrent.CountDownLatch(2); var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var tasks = java.util.stream.IntStream.range(0,2).mapToObj(number -> pool.submit(() -> {
                try {
                    transactions.execute(() -> {
                        var campaign = campaigns.findById(id).orElseThrow(); ready.countDown();
                        try { if (!start.await(5,java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Timed out"); }
                        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
                        define(campaign,"Concurrent-"+number); campaigns.save(campaign); return null;
                    });
                    return "saved";
                } catch (org.springframework.orm.ObjectOptimisticLockingFailureException | org.springframework.dao.PessimisticLockingFailureException expected) {
                    return "conflict";
                }
            })).toList();
            assertTrue(ready.await(5,java.util.concurrent.TimeUnit.SECONDS)); start.countDown();
            var results = new java.util.ArrayList<String>();
            for(var task : tasks) results.add(task.get(20,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1,results.stream().filter("saved"::equals).count());
            assertEquals(1,results.stream().filter("conflict"::equals).count());
            assertEquals(1,campaigns.findById(id).orElseThrow().requirements().size());
        } finally { start.countDown(); pool.shutdownNow(); }
    }
    private void define(com.collabtech.platform.campaign.domain.model.aggregates.Campaign campaign,String text) {
        Instant now = Instant.now();
        campaign.defineConditions(List.of(new CampaignRequirement(new RequirementId(UUID.randomUUID()),text,true)),
                List.of(new DeliverableSpecification(new SpecificationId(UUID.randomUUID()),"VIDEO","Video",1,now.plusSeconds(7200))),
                now.plusSeconds(3600),new CompensationTerms(CompensationType.CASH,new BigDecimal("500"),"PEN","Pago"),now);
    }
    private Account account(String role) throws Exception {
        String email = "campaign-"+UUID.randomUUID()+"@example.com";
        String name = role.equals("brands") ? "businessName" : "displayName";
        mvc.perform(post("/api/v1/auth/"+role).contentType(MediaType.APPLICATION_JSON).content("{\""+name+"\":\"Cuenta de prueba\",\"email\":\""+email+"\",\"password\":\"PasswordForCampaign123!\"}")).andExpect(status().isCreated());
        var login = mvc.perform(post("/api/v1/auth/sessions").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\""+email+"\",\"password\":\"PasswordForCampaign123!\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var body = json.readTree(login); return new Account(body.path("accessToken").asText(),body.path("account").path("profileId").asText(),body.path("account").path("accountId").asText());
    }
    private String create(String token) throws Exception {
        var result = mvc.perform(post("/api/v1/campaigns").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(METADATA))
                .andExpect(status().isCreated()).andExpect(header().exists("Location")).andReturn().getResponse();
        return json.readTree(result.getContentAsString()).path("id").asText();
    }
    private org.springframework.test.web.servlet.ResultActions conditions(String token,String id,String body) throws Exception {
        return mvc.perform(put("/api/v1/campaigns/"+id+"/conditions").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(body));
    }
    private String validConditions() {
        Instant now=Instant.now();
        return "{\"requirements\":[{\"description\":\"Afinidad con moda\",\"mandatory\":true}],\"deliverables\":[{\"contentType\":\"VIDEO\",\"description\":\"Video de producto\",\"quantity\":1,\"deadline\":\""+now.plusSeconds(7200)+"\"}],\"applicationDeadline\":\""+now.plusSeconds(3600)+"\",\"compensation\":{\"type\":\"CASH\",\"amount\":500.00,\"currency\":\"PEN\",\"description\":\"Pago por contenido\"}}";
    }
    private String published(String token) throws Exception { return mvc.perform(get("/api/v1/campaigns/published?size=100").header("Authorization",bearer(token))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(); }
    private static String bearer(String token) { return "Bearer "+token; }
    private record Account(String token,String profileId,String accountId) {}
}
