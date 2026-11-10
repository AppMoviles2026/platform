package com.collabtech.platform.performance.infrastructure.attribution;
import com.collabtech.platform.performance.application.ports.AttributionPorts.*;
import java.net.URI;
import java.util.*;

/** Clients select server-defined IDs, never redirect URLs or host wildcards. No outbound request is made. */
public final class ConfiguredAttributionDestinations implements Destinations {
    private final Map<String,String> destinations;private final String publicBase;
    public ConfiguredAttributionDestinations(String configured,String publicBase){
        var base=URI.create(publicBase);
        if(!Set.of("https","http").contains(base.getScheme())||base.getHost()==null||base.getUserInfo()!=null
                ||base.getRawQuery()!=null||base.getRawFragment()!=null||!Set.of("","/").contains(base.getPath()))
            throw new IllegalArgumentException("Attribution public base URL must contain only scheme, host and optional port");
        this.publicBase=publicBase.replaceAll("/$","");
        var values=new TreeMap<String,String>();
        if(configured!=null&&!configured.isBlank())for(String entry:configured.split(",")){
            String[] parts=entry.strip().split("=",2);
            if(parts.length!=2||!parts[0].matches("[a-z0-9-]{1,40}")||values.containsKey(parts[0]))throw new IllegalArgumentException("Invalid attribution destination ID");
            var uri=URI.create(parts[1]);
            String host=uri.getHost();
            if(!"https".equals(uri.getScheme())||host==null||host.equalsIgnoreCase("localhost")||!host.contains(".")
                    ||host.matches("[0-9.]+")||host.contains(":")||uri.getUserInfo()!=null||uri.getRawFragment()!=null
                    ||uri.getPort()!=-1&&uri.getPort()!=443||parts[1].length()>2048||!uri.normalize().equals(uri))
                throw new IllegalArgumentException("Attribution destinations must be explicit HTTPS URLs");
            values.put(parts[0],uri.toASCIIString());
        }
        destinations=Collections.unmodifiableMap(values);
    }
    public List<Destination> available(){return destinations.entrySet().stream().map(e->new Destination(e.getKey(),e.getValue())).toList();}
    public Optional<String> resolve(String id){return Optional.ofNullable(id==null?null:destinations.get(id));}
    public String publicUrl(String token){return publicBase+"/api/v1/public/attribution/"+token;}
    public boolean secureCookie(){return publicBase.startsWith("https://");}
}
