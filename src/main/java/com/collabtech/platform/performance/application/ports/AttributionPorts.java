package com.collabtech.platform.performance.application.ports;
import com.collabtech.platform.performance.domain.model.AttributionModels.*;
import com.collabtech.platform.performance.domain.model.PerformanceModels.Period;
import com.collabtech.platform.shared.application.pagination.*;
import java.util.*;
public final class AttributionPorts {
    private AttributionPorts(){}
    public record Destination(String id,String url){}
    public interface Destinations {
        List<Destination> available();
        Optional<String> resolve(String id);
        String publicUrl(String token);
        boolean secureCookie();
    }
    public interface Identifiers {String token();String code();}
    public interface Queries {
        PageResult<Mechanism> mechanisms(UUID report,PageRequest page);
        Counts counts(UUID report,UUID mechanism,Period period,boolean demonstration);
        PageResult<Interaction> interactions(UUID report,UUID mechanism,Period period,PageRequest page);
    }
}
