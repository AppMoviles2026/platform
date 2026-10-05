package com.collabtech.platform.campaign;
import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT) @ActiveProfiles("test")
class CampaignDiscoveryApplicationApiTests {
    @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @LocalServerPort int port;
    @Autowired com.collabtech.platform.campaign.domain.repositories.ApplicationRepository applications;
    @Autowired com.collabtech.platform.campaign.application.ports.CampaignUnitOfWork transactions;
    private final HttpClient client=HttpClient.newHttpClient();
    private record Result(int status,JsonNode body) {}
    private record Account(String token,String profileId) {}
    private record Fixture(Account brand,Account creator,String campaign,String requirement,String title) {}
    private Result call(String method,String path,String token,String body) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(java.time.Duration.ofSeconds(30));
        if(token!=null) request.header("Authorization","Bearer "+token);
        if(body!=null) request.header("Content-Type","application/json");
        request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
        var response=client.send(request.build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Result(response.statusCode(),json.readTree(response.body()));
    }
    private Account account(String role) throws Exception {
        var email="discovery-"+UUID.randomUUID()+"@example.com";
        var data=json.createObjectNode().put(role.equals("brands")?"businessName":"displayName","Marca "+UUID.randomUUID()).put("email",email).put("password","PasswordCampaign123!");
        assertEquals(201,call("POST","/api/v1/auth/"+role,null,data.toString()).status);
        var login=call("POST","/api/v1/auth/sessions",null,json.createObjectNode().put("email",email).put("password","PasswordCampaign123!").toString());
        assertEquals(200,login.status); return new Account(login.body.path("accessToken").asText(),login.body.path("account").path("profileId").asText());
    }
    private Fixture fixture(String rule,String expected,boolean mandatory,boolean publish) throws Exception {
        var brand=account("brands"); var creator=account("creators"); String title="Búsqueda "+UUID.randomUUID()+" 100%_real";
        var metadata=json.createObjectNode().put("title",title).put("objective","Promover producto").put("category","Moda").put("targetAudience","Adultos").put("location","Lima");
        var draft=call("POST","/api/v1/campaigns",brand.token,metadata.toString()); assertEquals(201,draft.status); String id=draft.body.path("id").asText();
        var conditions=json.createObjectNode();
        var requirement=conditions.putArray("requirements").addObject().put("description","Condición verificable").put("mandatory",mandatory).put("ruleType",rule);
        if(expected!=null) requirement.put("expectedValue",expected);
        conditions.putArray("deliverables").addObject().put("contentType","VIDEO").put("description","Video").put("quantity",1).put("deadline",Instant.now().plusSeconds(7200).toString());
        conditions.put("applicationDeadline",Instant.now().plusSeconds(3600).toString());
        conditions.putObject("compensation").put("type","CASH").put("amount",100).put("currency","PEN").put("description","Pago por video");
        var defined=call("PUT","/api/v1/campaigns/"+id+"/conditions",brand.token,conditions.toString()); assertEquals(200,defined.status);
        if(publish) assertEquals(200,call("POST","/api/v1/campaigns/"+id+"/publication",brand.token,null).status);
        return new Fixture(brand,creator,id,defined.body.path("requirements").path(0).path("id").asText(),title);
    }
    private Fixture fixture() throws Exception { return fixture("MANUAL_CONFIRMATION",null,true,true); }
    private String proposal(Fixture f,boolean confirm) {
        var body=json.createObjectNode().put("message","Mi propuesta");
        if(confirm) body.putArray("confirmedRequirementIds").add(f.requirement);
        return body.toString();
    }
    private Result submit(Fixture f,boolean confirm) throws Exception { return call("POST","/api/v1/campaigns/"+f.campaign+"/applications",f.creator.token,proposal(f,confirm)); }
    private String enc(String value) { return URLEncoder.encode(value,StandardCharsets.UTF_8); }
    @Test void realHttpEndToEndSearchDetailsSubmitReadEditCancel() throws Exception {
        var f=fixture();
        var search=call("GET","/api/v1/campaigns?q="+enc(f.title)+"&category=moda&location=lima&compensationType=CASH&size=1",f.creator.token,null);
        assertEquals(200,search.status); assertEquals(1,search.body.path("total").asInt()); assertEquals(f.campaign,search.body.path("items").path(0).path("id").asText());
        var detail=call("GET","/api/v1/campaigns/"+f.campaign,f.creator.token,null);
        assertEquals(200,detail.status); assertTrue(detail.body.path("acceptsApplications").asBoolean()); assertEquals("MANUAL_CONFIRMATION",detail.body.path("requirements").path(0).path("ruleType").asText());
        var created=submit(f,true); assertEquals(201,created.status); var id=created.body.path("id").asText();
        assertEquals(f.creator.profileId,created.body.path("creatorId").asText()); assertEquals("PENDING",created.body.path("status").asText());
        assertEquals(1,jdbc.queryForObject("select count(*) from campaign_application_confirmation where application_id=?",Integer.class,id));
        assertEquals(200,call("GET","/api/v1/applications/"+id,f.creator.token,null).status);
        var mine=call("GET","/api/v1/applications/mine?size=1",f.creator.token,null); assertEquals(1,mine.body.path("total").asInt());
        var edited=call("PUT","/api/v1/applications/"+id,f.creator.token,"{\"message\":\"Propuesta actualizada\",\"expectedVersion\":0}");
        assertEquals(200,edited.status); assertEquals(1,edited.body.path("version").asInt()); assertEquals("Propuesta actualizada",edited.body.path("message").asText());
        var cancelled=call("POST","/api/v1/applications/"+id+"/cancellation",f.creator.token,"{\"expectedVersion\":1}");
        assertEquals(200,cancelled.status); assertEquals("CANCELLED",cancelled.body.path("status").asText());
        assertEquals("CANCELLED",jdbc.queryForObject("select status from campaign_application where application_id=?",String.class,id));
    }
    @Test void missingMandatoryRequirementIdentifiesConditionAndWritesNothing() throws Exception {
        var f=fixture(); var result=submit(f,false); assertEquals(422,result.status);
        assertEquals("REQUIREMENTS_NOT_MET",result.body.path("code").asText()); assertEquals("Condición verificable",result.body.path("fieldErrors").path("requirements."+f.requirement).asText());
        assertEquals(0,jdbc.queryForObject("select count(*) from campaign_application where campaign_id=?",Integer.class,f.campaign));
    }
    @Test void staleAggregateAndRollbackPreserveStoredProposal() throws Exception {
        var f=fixture(); var id=new com.collabtech.platform.campaign.domain.model.valueobjects.ApplicationId(UUID.fromString(submit(f,true).body.path("id").asText()));
        var fresh=applications.findById(id).orElseThrow(); var stale=applications.findById(id).orElseThrow();
        fresh.updateMessage("Guardada"); applications.save(fresh); stale.updateMessage("Obsoleta");
        assertThrows(com.collabtech.platform.campaign.domain.exceptions.CampaignFailure.class,() -> applications.save(stale));
        assertThrows(IllegalStateException.class,() -> transactions.execute(() -> {
            var current=applications.findById(id).orElseThrow(); current.cancel(); applications.save(current);
            throw new IllegalStateException("Rollback after save");
        }));
        assertEquals("Guardada",applications.findById(id).orElseThrow().message());
        assertEquals(com.collabtech.platform.campaign.domain.model.valueobjects.ApplicationStatus.PENDING,applications.findById(id).orElseThrow().status());
    }
    @Test void brandSnapshotIsSearchableAndWildcardsRemainLiteral() throws Exception {
        var f=fixture();
        var detail=call("GET","/api/v1/campaigns/"+f.campaign,f.creator.token,null);
        String name=detail.body.path("brandName").asText();
        assertEquals(1,call("GET","/api/v1/campaigns?q="+enc(name),f.creator.token,null).body.path("total").asInt());
        String uniqueTitle=f.title.substring(0,f.title.indexOf(" 100%_real"));
        assertEquals(0,call("GET","/api/v1/campaigns?q="+enc(uniqueTitle+" 100%Xreal"),f.creator.token,null).body.path("total").asInt());
        assertEquals(0,call("GET","/api/v1/campaigns?q="+enc(f.title)+"&location=Cusco",f.creator.token,null).body.path("total").asInt());
        assertEquals(0,call("GET","/api/v1/campaigns?q="+enc(f.title)+"&compensationType=PRODUCT",f.creator.token,null).body.path("total").asInt());
    }
    @Test void optionalManualConditionDoesNotBlock() throws Exception { assertEquals(201,submit(fixture("MANUAL_CONFIRMATION",null,false,true),false).status); }
    @Test void duplicateRemainsRejectedAfterCancellation() throws Exception {
        var f=fixture(); var first=submit(f,true); String id=first.body.path("id").asText();
        assertEquals(409,submit(f,true).status); assertEquals(200,call("POST","/api/v1/applications/"+id+"/cancellation",f.creator.token,null).status);
        assertEquals(409,submit(f,true).status); assertEquals(1,jdbc.queryForObject("select count(*) from campaign_application where campaign_id=?",Integer.class,f.campaign));
    }
    @Test void unknownConfirmationAndForgedOwnershipAreRejected() throws Exception {
        var f=fixture();
        assertEquals(400,call("POST","/api/v1/campaigns/"+f.campaign+"/applications",f.creator.token,"{\"message\":\"Propuesta\",\"confirmedRequirementIds\":[\""+UUID.randomUUID()+"\"]}").status);
        assertEquals(400,call("POST","/api/v1/campaigns/"+f.campaign+"/applications",f.creator.token,"{\"message\":\"Propuesta\",\"creatorId\":\""+UUID.randomUUID()+"\"}").status);
    }
    @ParameterizedTest @ValueSource(strings={"DRAFT","CLOSED","CANCELLED","EXPIRED"})
    void campaignMustBeAcceptingApplications(String state) throws Exception {
        var f=fixture();
        if(state.equals("EXPIRED")) jdbc.update("update campaign_campaign set application_deadline=? where campaign_id=?",Timestamp.from(Instant.now().minusSeconds(10)),f.campaign);
        else jdbc.update("update campaign_campaign set status=? where campaign_id=?",state,f.campaign);
        assertEquals(409,submit(f,true).status);
        var detail=call("GET","/api/v1/campaigns/"+f.campaign,f.creator.token,null);
        if(state.equals("DRAFT")) assertEquals(403,detail.status); else { assertEquals(200,detail.status); assertFalse(detail.body.path("acceptsApplications").asBoolean()); }
    }
    @ParameterizedTest @ValueSource(strings={"CANCELLED","SELECTED","REJECTED"})
    void terminalApplicationsCannotBeEditedOrCancelled(String state) throws Exception {
        var f=fixture(); var id=submit(f,true).body.path("id").asText();
        jdbc.update("update campaign_application set status=? where application_id=?",state,id);
        assertEquals(409,call("PUT","/api/v1/applications/"+id,f.creator.token,"{\"message\":\"Cambio\"}").status);
        assertEquals(409,call("POST","/api/v1/applications/"+id+"/cancellation",f.creator.token,null).status);
    }
    @Test void anotherCreatorAndBrandCannotAccessOrMutateApplication() throws Exception {
        var f=fixture(); var other=account("creators"); var id=submit(f,true).body.path("id").asText();
        assertEquals(404,call("GET","/api/v1/applications/"+id,other.token,null).status);
        assertEquals(403,call("PUT","/api/v1/applications/"+id,other.token,"{\"message\":\"Robo\"}").status);
        assertEquals(403,call("POST","/api/v1/applications/"+id+"/cancellation",other.token,null).status);
        assertEquals(0,call("GET","/api/v1/applications/mine",other.token,null).body.path("total").asInt());
        assertEquals(403,call("POST","/api/v1/campaigns/"+f.campaign+"/applications",f.brand.token,proposal(f,true)).status);
        assertEquals(403,call("GET","/api/v1/campaigns",f.brand.token,null).status);
        assertEquals(403,call("GET","/api/v1/applications/mine",f.brand.token,null).status);
    }
    @Test void automaticNicheAndLocationUseCurrentIdentityFacts() throws Exception {
        for(String rule:List.of("NICHE_EQUALS","LOCATION_EQUALS")) {
            var f=fixture(rule,rule.equals("NICHE_EQUALS")?"Moda":"Lima",true,true);
            assertEquals(422,submit(f,false).status);
            var profile=call("PUT","/api/v1/profiles/me/creator",f.creator.token,"{\"displayName\":\"Creador\",\"niche\":\"moda\",\"location\":\"lima\",\"audienceDescription\":\"100000 seguidores (no verificados)\"}");
            assertEquals(200,profile.status); assertEquals(201,submit(f,false).status);
        }
    }
    @Test void manualConfirmationCannotBypassAutomaticSocialRule() throws Exception {
        var f=fixture("AUTHORIZED_PLATFORM","instagram",true,true);
        assertEquals(422,submit(f,false).status); assertEquals(400,submit(f,true).status);
    }
    @Test void staleVersionDoesNotOverwriteProposal() throws Exception {
        var f=fixture(); var id=submit(f,true).body.path("id").asText();
        assertEquals(200,call("PUT","/api/v1/applications/"+id,f.creator.token,"{\"message\":\"Nueva\",\"expectedVersion\":0}").status);
        assertEquals(409,call("PUT","/api/v1/applications/"+id,f.creator.token,"{\"message\":\"Obsoleta\",\"expectedVersion\":0}").status);
        assertEquals(409,call("POST","/api/v1/applications/"+id+"/cancellation",f.creator.token,"{\"expectedVersion\":0}").status);
        assertEquals("Nueva",call("GET","/api/v1/applications/"+id,f.creator.token,null).body.path("message").asText());
    }
    @Test void simultaneousSubmissionsProduceOnlyOneApplication() throws Exception {
        var f=fixture(); var pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            var first=pool.submit(() -> { start.await(); return submit(f,true).status; });
            var second=pool.submit(() -> { start.await(); return submit(f,true).status; });
            start.countDown(); var statuses=new ArrayList<>(List.of(first.get(30,TimeUnit.SECONDS),second.get(30,TimeUnit.SECONDS)));
            Collections.sort(statuses); assertEquals(List.of(201,409),statuses);
            assertEquals(1,jdbc.queryForObject("select count(*) from campaign_application where campaign_id=?",Integer.class,f.campaign));
        } finally { start.countDown(); pool.shutdownNow(); }
    }
    @Test void concurrentEditAndCancelWithSameVersionCannotBothSucceed() throws Exception {
        var f=fixture(); var id=submit(f,true).body.path("id").asText(); var pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            var first=pool.submit(() -> { start.await(); return call("PUT","/api/v1/applications/"+id,f.creator.token,"{\"message\":\"Concurrente\",\"expectedVersion\":0}").status; });
            var second=pool.submit(() -> { start.await(); return call("POST","/api/v1/applications/"+id+"/cancellation",f.creator.token,"{\"expectedVersion\":0}").status; });
            start.countDown(); var statuses=new ArrayList<>(List.of(first.get(30,TimeUnit.SECONDS),second.get(30,TimeUnit.SECONDS)));
            Collections.sort(statuses); assertEquals(List.of(200,409),statuses);
        } finally { start.countDown(); pool.shutdownNow(); }
    }
    @Test void searchUsesLiteralWildcardsBoundParametersAndPaging() throws Exception {
        var f=fixture();
        assertEquals(1,call("GET","/api/v1/campaigns?q="+enc(f.title),f.creator.token,null).body.path("total").asInt());
        assertEquals(0,call("GET","/api/v1/campaigns?q="+enc("unknown "+UUID.randomUUID()),f.creator.token,null).body.path("total").asInt());
        assertEquals(0,call("GET","/api/v1/campaigns?q="+enc("' OR 1=1 --"),f.creator.token,null).body.path("total").asInt());
        assertEquals(0,call("GET","/api/v1/campaigns?q="+enc(f.title)+"&category=Belleza",f.creator.token,null).body.path("total").asInt());
        var next=call("GET","/api/v1/campaigns?q="+enc(f.title)+"&page=1&size=1",f.creator.token,null);
        assertEquals(1,next.body.path("total").asInt()); assertEquals(0,next.body.path("items").size());
        jdbc.update("update campaign_campaign set status='CLOSED' where campaign_id=?",f.campaign);
        assertEquals(0,call("GET","/api/v1/campaigns?q="+enc(f.title),f.creator.token,null).body.path("total").asInt());
    }
    @Test void invalidRequestsAndAuthenticationHaveSafeUtf8Errors() throws Exception {
        var f=fixture();
        var anonymous=call("GET","/api/v1/campaigns",null,null); assertEquals(401,anonymous.status);
        assertEquals("Se requiere una sesión válida.",anonymous.body.path("message").asText());
        assertEquals(401,call("GET","/api/v1/campaigns","invalid",null).status);
        assertEquals(400,call("GET","/api/v1/campaigns?page=-1",f.creator.token,null).status);
        assertEquals(400,call("GET","/api/v1/campaigns?q="+enc("x".repeat(201)),f.creator.token,null).status);
        assertEquals(400,call("GET","/api/v1/applications/mine?size=101",f.creator.token,null).status);
        assertEquals(400,call("POST","/api/v1/campaigns/"+f.campaign+"/applications",f.creator.token,"{\"message\":\" \"}").status);
        assertEquals(404,call("POST","/api/v1/campaigns/"+UUID.randomUUID()+"/applications",f.creator.token,proposal(f,true)).status);
        assertEquals(404,call("GET","/api/v1/applications/"+UUID.randomUUID(),f.creator.token,null).status);
    }
}

