package com.collabtech.platform.billing.infrastructure.configuration;
import com.collabtech.platform.billing.application.ports.SubscriptionPorts.Store;
import com.collabtech.platform.billing.application.ports.BillingPorts.*;
import com.collabtech.platform.billing.domain.model.SubscriptionModels.Plan;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
@Component @Profile("!skeleton")
public class SubscriptionCatalogSeed implements ApplicationRunner {
    private final Store store;private final UnitOfWork work;private final Secrets secrets;private final boolean enabled;
    private final String essential,growth,essentialAmount,growthAmount;
    public SubscriptionCatalogSeed(Store store,UnitOfWork work,Secrets secrets,@Value("${billing.catalog.demo-enabled:false}")boolean enabled,
        @Value("${billing.catalog.essential.provider-plan-id:}")String essential,@Value("${billing.catalog.growth.provider-plan-id:}")String growth,
        @Value("${billing.catalog.essential.amount:5.00}")String essentialAmount,@Value("${billing.catalog.growth.amount:10.00}")String growthAmount){
        this.store=store;this.work=work;this.secrets=secrets;this.enabled=enabled;this.essential=essential;this.growth=growth;this.essentialAmount=essentialAmount;this.growthAmount=growthAmount;
    }
    public void run(ApplicationArguments args){if(enabled)work.execute(()->{store.deactivateDemoCatalog();
        store.catalog(plan("ESSENTIAL","Esencial","Para comenzar con campañas pequeñas.",essentialAmount,essential,List.of("Gestión de campañas y colaboraciones de demostración")));
        store.catalog(plan("GROWTH","Crecimiento","Para gestionar colaboraciones y consultar resultados.",growthAmount,growth,List.of("Gestión de campañas y colaboraciones de demostración","Consulta de resultados disponibles en esta versión")));
        return null;});}
    private Plan plan(String code,String name,String description,String amount,String provider,List<String> benefits){
        var price=new BigDecimal(amount).setScale(2,java.math.RoundingMode.UNNECESSARY);
        String hash=secrets.fingerprint(Map.of("code",code,"name",name,"description",description,"amount",price.toPlainString(),"currency","USD","provider",provider,"benefits",benefits));
        return new Plan(UUID.nameUUIDFromBytes(hash.getBytes(StandardCharsets.UTF_8)),code,name,description,price,"USD",benefits,provider,true,hash);
    }
}
