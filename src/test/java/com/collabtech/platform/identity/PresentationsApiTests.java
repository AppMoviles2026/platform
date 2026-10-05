package com.collabtech.platform.identity;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Tests the real controller, query handler, classpath catalog and JSON serialization. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PresentationsApiTests {
    @LocalServerPort
    private int port;

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void us01PublicVisitorCanReadProfessionalAndFairBrandValueProposition() throws Exception {
        mvc.perform(get("/api/v1/public/presentations/brands"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.audience").value("BRAND"))
                .andExpect(jsonPath("$.language").value("es"))
                .andExpect(jsonPath("$.title").value("Más control para tu marca"))
                .andExpect(jsonPath("$.valueProposition", containsString("profesional y justa")))
                .andExpect(jsonPath("$.valueProposition", containsString("creadores de contenido")));
    }

    @Test
    void us01BenefitsExplainCampaignsRequirementsDeliverablesCompensationAndTracking() throws Exception {
        mvc.perform(get("/api/v1/public/presentations/brands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.benefits[*].code",
                        contains("campaigns", "requirements", "deliverables", "compensation", "tracking")))
                .andExpect(jsonPath("$.benefits[0].description", containsString("campañas")))
                .andExpect(jsonPath("$.benefits[1].description", containsString("cumplir")))
                .andExpect(jsonPath("$.benefits[2].description", containsString("contenido esperado")))
                .andExpect(jsonPath("$.benefits[3].description", containsString("intercambio")))
                .andExpect(jsonPath("$.benefits[4].description", containsString("avance")));
    }

    @Test
    void us02PublicVisitorCanReadCreatorValueProposition() throws Exception {
        mvc.perform(get("/api/v1/public/presentations/creators"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.audience").value("CREATOR"))
                .andExpect(jsonPath("$.language").value("es"))
                .andExpect(jsonPath("$.title").value("Más oportunidades para tu contenido"))
                .andExpect(jsonPath("$.valueProposition", containsString("encontrar y gestionar")))
                .andExpect(jsonPath("$.valueProposition", containsString("empresas")));
    }

    @Test
    void us02BenefitsDescribeTermsBeforeAcceptingCollaboration() throws Exception {
        mvc.perform(get("/api/v1/public/presentations/creators"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.benefits[*].code",
                        contains("discovery", "management", "requirements", "deliverables", "dates", "compensation")))
                .andExpect(jsonPath("$.benefits[2].description", containsString("antes de aceptar")))
                .andExpect(jsonPath("$.benefits[3].description", containsString("antes de comprometerte")))
                .andExpect(jsonPath("$.benefits[4].description", containsString("fechas")))
                .andExpect(jsonPath("$.benefits[5].description", containsString("antes de aceptar")));
    }

    @Test
    void contentApiIsReadOnly() throws Exception {
        mvc.perform(post("/api/v1/public/presentations/brands"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void unknownAudienceRouteIsNotPublished() throws Exception {
        mvc.perform(get("/api/v1/public/presentations/unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    void bothPublicPresentationsAreAvailableOverRealHttpWithoutDatabaseOrSession() throws Exception {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        for (String audience : new String[]{"brands", "creators"}) {
            var request = HttpRequest.newBuilder(URI.create(
                    "http://localhost:" + port + "/api/v1/public/presentations/" + audience))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(200, response.statusCode(), audience);
            assertTrue(response.headers().firstValue("content-type").orElse("").startsWith("application/json"));
            assertTrue(response.body().contains(audience.equals("brands")
                    ? "Más control para tu marca" : "Más oportunidades para tu contenido"));
        }
    }
}
