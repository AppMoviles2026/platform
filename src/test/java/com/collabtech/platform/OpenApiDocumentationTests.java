package com.collabtech.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class OpenApiDocumentationTests {
    @Autowired WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test void documentationIsPublicAndDescribesBearerSecurity() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("CollabPro API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/auth/sessions'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/accounts/me'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/agreements'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/agreements/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/agreements/mine'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/agreements/{id}/decisions'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/collaborations'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/collaborations/mine'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/collaborations/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/campaigns/{id}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/campaigns/{id}/applications/{applicationId}/decision'].post").exists());
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/profiles/me/brand'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/profiles/me/brand'].put").exists())
                .andExpect(jsonPath("$.components.schemas.BrandProfileUpdate.required",org.hamcrest.Matchers.hasItem("expectedVersion")))
                .andExpect(jsonPath("$.components.schemas.BrandProfile.properties.version").exists())
                .andExpect(jsonPath("$.paths['/api/v1/social-accounts/{socialId}/access'].get").exists());

        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/collaborations/{id}/deliverables'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/collaborations/{id}/deliverables'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/deliverables/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/deliverables/{id}/versions'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/deliverables/{id}/versions'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/deliverables/{id}/validation'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/deliverables/{id}/rejection'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/collaborations/{id}/assets'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/collaboration-assets/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/collaboration-assets/{id}/content'].get.responses['200'].content['application/pdf'].schema.format").value("binary"))
                .andExpect(jsonPath("$.components.schemas.RejectionBody.required",org.hamcrest.Matchers.hasItem("correctionDeadline")))
                .andExpect(jsonPath("$.components.schemas.ApprovalBody.required",org.hamcrest.Matchers.hasItem("submissionId")));

        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/collaborations/{id}/incidents'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/collaborations/{id}/incidents'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/incidents/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/incidents/{id}/resolutions'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/incidents/{id}/resolutions'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/incidents/{id}/resolutions/{resolutionId}/decisions'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/collaborations/{id}/incident-assets'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/collaborations/history'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/collaborations/history/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/collaborations/{id}/history'].get").exists());
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/collaborations/{id}/metrics'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/collaborations/{id}/metrics/refresh'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/collaborations/{id}/metrics/snapshots'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/collaborations/{id}/evidence'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/collaborations/{id}/evidence'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/performance-evidence/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/performance-evidence/{id}/verification'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/collaborations/{id}/evidence-assets'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/performance-assets/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/performance-assets/{id}/content'].get.responses['200'].content['application/pdf'].schema.format").value("binary"))
                .andExpect(jsonPath("$.components.schemas.EvidenceBody.required",org.hamcrest.Matchers.hasItems("observedAt","period","references")))
                .andExpect(jsonPath("$.components.schemas.RefreshBody.required",org.hamcrest.Matchers.hasItems("requestId","socialId","submissionId","postId")));
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/collaborations/{id}/attribution'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/collaborations/{id}/attribution'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/attribution/destinations'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/attribution/{id}/status'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/attribution/{id}/interactions'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/attribution/{id}/interactions'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/public/attribution/{token}'].get.responses['302']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/public/attribution/{token}'].get.security").isEmpty())
                .andExpect(jsonPath("$.components.schemas.AttributionCreateBody.required",org.hamcrest.Matchers.hasItems("requestId","kind","label")))
                .andExpect(jsonPath("$.components.schemas.AttributionInteractionBody.required",org.hamcrest.Matchers.hasItems("eventId","evidenceId","occurredAt")))
                .andExpect(jsonPath("$.components.schemas.AttributionStatusBody.required",org.hamcrest.Matchers.hasItem("expectedVersion")));
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/user/{id}/billing'].post.responses['202']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/user/{id}/billing'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/user/{id}/billing/validation'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/billing/authorizations/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/billing/paypal/callback'].get.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/billing/paypal/identity-callback'].get.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/webhooks/payments'].post.security").isEmpty())
                .andExpect(jsonPath("$.components.schemas.BeginRequest.required",org.hamcrest.Matchers.hasItems("requestId","client")))
                .andExpect(jsonPath("$.components.schemas.MethodView.properties.encryptedReference").doesNotExist());
        mvc.perform(get("/api/v1/accounts/me")).andExpect(status().isUnauthorized());
    }
}
