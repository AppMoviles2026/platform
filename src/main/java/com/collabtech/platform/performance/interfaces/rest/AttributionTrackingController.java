package com.collabtech.platform.performance.interfaces.rest;
import com.collabtech.platform.performance.application.services.AttributionService;
import com.collabtech.platform.performance.application.ports.AttributionPorts.Destinations;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @Profile("!skeleton") @RequestMapping("/api/v1/public/attribution")
public class AttributionTrackingController {
    public static final String COOKIE="cp_attribution_session";
    private final AttributionService service;private final Destinations destinations;
    public AttributionTrackingController(AttributionService service,Destinations destinations){this.service=service;this.destinations=destinations;}
    @RequestMapping(value="/{token}",method={RequestMethod.GET,RequestMethod.HEAD}) @SecurityRequirements
    @Operation(summary="Abrir el destino fijo del enlace de atribución",description="Público. Redirección 302 sin cache. HEAD y prefetch no registran clics. Cookie de sesión opaca para deduplicar por enlace; clientes pueden usar Idempotency-Key UUID estable en reintentos.")
    @ApiResponse(responseCode="302",description="Destino previamente aprobado; no devuelve resultados privados")
    @ApiResponse(responseCode="410",description="Mecanismo inactivo, cancelado o destino retirado")
    public ResponseEntity<Void> visit(@PathVariable String token,@RequestHeader(value="Idempotency-Key",required=false)UUID key,
            @CookieValue(value=COOKIE,required=false)String session,HttpServletRequest request){
        if(!request.getParameterMap().isEmpty())throw new IllegalArgumentException("Tracking links have no query parameters");
        boolean count=!request.getMethod().equals("HEAD")&&!prefetch(request.getHeader("Purpose"))&&!prefetch(request.getHeader("Sec-Purpose"));
        boolean fresh=session==null||!session.matches("[a-fA-F0-9-]{36}");
        UUID visitor;
        try{visitor=fresh?UUID.randomUUID():UUID.fromString(session);}catch(IllegalArgumentException invalid){visitor=UUID.randomUUID();fresh=true;}
        var redirect=service.redirect(token,key==null?"session:"+visitor:"retry:"+key,count);
        var response=ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirect.destination())).cacheControl(CacheControl.noStore())
                .header("Referrer-Policy","no-referrer").header("X-Content-Type-Options","nosniff");
        if(count&&key==null&&fresh)response.header(HttpHeaders.SET_COOKIE,ResponseCookie.from(COOKIE,visitor.toString()).httpOnly(true)
                .secure(destinations.secureCookie()).sameSite("Lax").path("/api/v1/public/attribution").build().toString());
        return response.build();
    }
    private boolean prefetch(String value){return value!=null&&value.toLowerCase(Locale.ROOT).contains("prefetch");}
}
