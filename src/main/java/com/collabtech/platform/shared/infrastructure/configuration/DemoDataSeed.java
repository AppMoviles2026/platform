package com.collabtech.platform.shared.infrastructure.configuration;

import com.collabtech.platform.campaign.domain.model.aggregates.Application;
import com.collabtech.platform.campaign.domain.model.aggregates.Campaign;
import com.collabtech.platform.campaign.domain.model.entities.CampaignRequirement;
import com.collabtech.platform.campaign.domain.model.entities.DeliverableSpecification;
import com.collabtech.platform.campaign.application.ports.CampaignCatalog;
import com.collabtech.platform.campaign.domain.model.valueobjects.*;
import com.collabtech.platform.campaign.domain.repositories.ApplicationRepository;
import com.collabtech.platform.campaign.domain.repositories.CampaignRepository;
import com.collabtech.platform.identity.application.ports.PasswordHasher;
import com.collabtech.platform.identity.domain.model.aggregates.Account;
import com.collabtech.platform.identity.domain.model.entities.BrandProfile;
import com.collabtech.platform.identity.domain.model.entities.CreatorProfile;
import com.collabtech.platform.identity.domain.model.valueobjects.*;
import com.collabtech.platform.identity.domain.repositories.AccountRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Opt-in sample data for local demos and explicitly enabled demo deployments. */
@Component
@Profile("!skeleton")
@ConditionalOnProperty(name = "collabpro.demo-seed.enabled", havingValue = "true")
public class DemoDataSeed implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeed.class);
    private static final String BRAND_EMAIL = "demo.brand@collabpro.app";
    private static final String CREATOR_ONE_EMAIL = "demo.creator@collabpro.app";
    private static final String CREATOR_TWO_EMAIL = "demo.creator2@collabpro.app";

    private static final UUID BRAND_ID = id("10000000-0000-4000-8000-000000000001");
    private static final UUID BRAND_PROFILE_ID = id("10000000-0000-4000-8000-000000000002");
    private static final UUID CREATOR_ONE_ID = id("20000000-0000-4000-8000-000000000001");
    private static final UUID CREATOR_ONE_PROFILE_ID = id("20000000-0000-4000-8000-000000000002");
    private static final UUID CREATOR_TWO_ID = id("20000000-0000-4000-8000-000000000003");
    private static final UUID CREATOR_TWO_PROFILE_ID = id("20000000-0000-4000-8000-000000000004");

    private final AccountRepository accounts;
    private final CampaignRepository campaigns;
    private final CampaignCatalog campaignCatalog;
    private final ApplicationRepository applications;
    private final PasswordHasher passwords;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final String demoPassword;

    public DemoDataSeed(AccountRepository accounts, CampaignRepository campaigns, CampaignCatalog campaignCatalog,
            ApplicationRepository applications, PasswordHasher passwords, Clock clock,
            PlatformTransactionManager transactionManager,
            @Value("${collabpro.demo-seed.password:}") String demoPassword) {
        this.accounts = accounts;
        this.campaigns = campaigns;
        this.campaignCatalog = campaignCatalog;
        this.applications = applications;
        this.passwords = passwords;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (demoPassword == null || demoPassword.length() < 12) {
            throw new IllegalStateException("DEMO_SEED_PASSWORD must be set to at least 12 characters when demo seed is enabled");
        }
        transaction.executeWithoutResult(status -> seed());
    }

    private void seed() {
        var now = clock.instant();
        var brand = ensureAccount(BRAND_ID, BRAND_EMAIL, AccountType.BRAND, now,
                new BrandProfile(new BrandProfileId(BRAND_PROFILE_ID), "Ayni Café", "Café peruano de especialidad y productos locales.", "Food & Beverage", "Lima, Perú"), null);
        var creatorOne = ensureAccount(CREATOR_ONE_ID, CREATOR_ONE_EMAIL, AccountType.CREATOR, now,
                null, new CreatorProfile(new CreatorProfileId(CREATOR_ONE_PROFILE_ID), "Lucía Mendoza",
                        "Creadora de contenido sobre café, gastronomía y vida local.", "Gastronomía", "Audiencia interesada en cafés y emprendimientos peruanos", "Lima, Perú", List.of()));
        var creatorTwo = ensureAccount(CREATOR_TWO_ID, CREATOR_TWO_EMAIL, AccountType.CREATOR, now,
                null, new CreatorProfile(new CreatorProfileId(CREATOR_TWO_PROFILE_ID), "Mateo Rojas",
                        "Videos cortos de comida, cultura y planes urbanos.", "Lifestyle", "Jóvenes profesionales de Lima", "Lima, Perú", List.of()));

        var campaignOne = ensureCampaign(brand.id().value(), now, 1,
                "Descubre el café peruano", "Dar a conocer el origen y el sabor de nuestros cafés.", "Food & Beverage",
                "Personas interesadas en café de especialidad", "Lima, Perú", CompensationType.CASH,
                new BigDecimal("450.00"), "PEN", "Pago por contenido aprobado", "Gastronomía", "Instagram Reel", 2);
        var campaignTwo = ensureCampaign(brand.id().value(), now, 2,
                "Un brunch con productos locales", "Promover una experiencia de brunch con ingredientes peruanos.", "Food & Beverage",
                "Personas que buscan lugares nuevos para brunch", "Lima, Perú", CompensationType.PRODUCT,
                null, null, "Consumo para dos personas en el local", "Lifestyle", "Instagram Story", 3);
        var campaignThree = ensureCampaign(brand.id().value(), now, 3,
                "Recetas rápidas con café", "Inspirar recetas sencillas para preparar en casa.", "Food & Beverage",
                "Audiencia interesada en recetas y café", "Arequipa, Perú", CompensationType.CASH,
                new BigDecimal("300.00"), "PEN", "Pago contra entrega aprobada", "Gastronomía", "Video corto", 4);
        ensureCampaign(brand.id().value(), now, 4,
                "Historias del barrio", "Contar historias de emprendimientos y cultura local.", "Lifestyle",
                "Personas que exploran negocios locales", "Lima, Perú", CompensationType.SERVICE,
                null, null, "Sesión de cata y experiencia para el creador", "Lifestyle", "Carrusel", 5);
        ensureDraftCampaign(brand.id().value());

        ensureApplication("30000000-0000-4000-8000-000000000001", campaignOne, creatorOne.id().value(),
                "Me gustaría contar la historia del café desde su origen hasta la preparación en casa.", now.minus(Duration.ofDays(2)));
        ensureApplication("30000000-0000-4000-8000-000000000002", campaignTwo, creatorOne.id().value(),
                "Puedo crear una reseña cercana del brunch y mostrar los productos locales.", now.minus(Duration.ofDays(1)));
        ensureApplication("30000000-0000-4000-8000-000000000003", campaignThree, creatorTwo.id().value(),
                "Propongo una receta breve y fácil de replicar, con un video dinámico.", now.minus(Duration.ofHours(8)));

        log.info("CollabPro demo seed is ready (1 brand, 2 creators, 5 campaigns, 3 applications)");
    }

    private Account ensureAccount(UUID accountId, String email, AccountType type, java.time.Instant now,
            BrandProfile brand, CreatorProfile creator) {
        return accounts.findByEmail(new EmailAddress(email)).orElseGet(() -> accounts.save(new Account(
                new AccountId(accountId), new EmailAddress(email), passwords.hash(demoPassword), type,
                AccountStatus.ACTIVE, now, brand, creator)));
    }

    private Campaign ensureCampaign(UUID brandId, java.time.Instant now, int index, String title, String objective,
            String category, String audience, String location, CompensationType compensationType,
            BigDecimal amount, String currency, String compensationDescription, String niche,
            String contentType, int daysFromNow) {
        UUID campaignUuid = id("40000000-0000-4000-8000-%012d".formatted(index));
        var campaignId = new CampaignId(campaignUuid);
        var existing = campaigns.findById(campaignId);
        if (existing.isPresent()) return existing.get();

        var requirementId = new RequirementId(id("50000000-0000-4000-8000-%012d".formatted(index)));
        var locationRequirementId = new RequirementId(id("60000000-0000-4000-8000-%012d".formatted(index)));
        var deadline = now.plus(Duration.ofDays(daysFromNow));
        var requirements = List.of(
                new CampaignRequirement(requirementId, "Crear contenido original sobre la campaña", true,
                        new RequirementRule(RequirementRule.Type.NICHE_EQUALS, niche)),
                new CampaignRequirement(locationRequirementId, "Confirmar disponibilidad para participar", true,
                        RequirementRule.manual()));
        var deliverables = List.of(new DeliverableSpecification(
                new SpecificationId(id("70000000-0000-4000-8000-%012d".formatted(index))), contentType,
                "Contenido publicado y enlace compartido con la marca", 1, deadline.plus(Duration.ofDays(5))));
        var campaign = new Campaign(campaignId, new BrandId(brandId), title, objective,
                "Colaboración de demostración de CollabPro. Coordina la propuesta directamente con la marca.",
                category, audience, location, CampaignStatus.OPEN, now.minus(Duration.ofDays(index)), deadline,
                requirements, deliverables, new CompensationTerms(compensationType, amount, currency, compensationDescription));
        campaignCatalog.saveNew(campaign, "Ayni Café");
        return campaign;
    }

    private void ensureDraftCampaign(UUID brandId) {
        var campaignId = new CampaignId(id("40000000-0000-4000-8000-000000000005"));
        if (campaigns.findById(campaignId).isPresent()) return;
        campaignCatalog.saveNew(new Campaign(campaignId, new BrandId(brandId), "Nueva campaña de temporada",
                "Preparar una colaboración para presentar la nueva carta.",
                "Borrador de demostración: completa condiciones y entregables antes de publicar.",
                "Food & Beverage", "Personas interesadas en novedades gastronómicas", "Lima, Perú",
                CampaignStatus.DRAFT, null, null, List.of(), List.of(), null), "Ayni Café");
    }

    private void ensureApplication(String applicationId, Campaign campaign, UUID creatorId, String message,
            java.time.Instant submittedAt) {
        var id = new ApplicationId(UUID.fromString(applicationId));
        if (applications.findById(id).isPresent()) return;
        applications.save(new Application(id, campaign.id(), new CreatorId(creatorId), message,
                ApplicationStatus.PENDING, submittedAt,
                Set.of(campaign.requirements().get(1).id())));
    }

    private static UUID id(String value) { return UUID.fromString(value); }
}
