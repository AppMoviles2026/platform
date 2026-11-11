package com.collabtech.platform.billing.interfaces.rest;

import com.collabtech.platform.billing.application.services.BillingService;
import com.collabtech.platform.billing.application.ports.BillingPorts.Signature;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure.Code;
import java.nio.charset.StandardCharsets;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.context.annotation.Profile;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.type.TypeReference;
import tools.jackson.core.json.JsonFactory;

@RestController @Profile("!skeleton")
public class PaymentsWebhookController {
    private final BillingService service;private final ObjectMapper json;
    public PaymentsWebhookController(BillingService service){this.service=service;this.json=new ObjectMapper(JsonFactory.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());}
    @io.swagger.v3.oas.annotations.Operation(summary="Recibir evento PayPal firmado; sin JWT",
        requestBody=@io.swagger.v3.oas.annotations.parameters.RequestBody(required=true,
            content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/json",
                schema=@io.swagger.v3.oas.annotations.media.Schema(type="object"))))
    @io.swagger.v3.oas.annotations.Parameters({
        @io.swagger.v3.oas.annotations.Parameter(name="PAYPAL-AUTH-ALGO",in=io.swagger.v3.oas.annotations.enums.ParameterIn.HEADER,required=true),
        @io.swagger.v3.oas.annotations.Parameter(name="PAYPAL-CERT-URL",in=io.swagger.v3.oas.annotations.enums.ParameterIn.HEADER,required=true),
        @io.swagger.v3.oas.annotations.Parameter(name="PAYPAL-TRANSMISSION-ID",in=io.swagger.v3.oas.annotations.enums.ParameterIn.HEADER,required=true),
        @io.swagger.v3.oas.annotations.Parameter(name="PAYPAL-TRANSMISSION-SIG",in=io.swagger.v3.oas.annotations.enums.ParameterIn.HEADER,required=true),
        @io.swagger.v3.oas.annotations.Parameter(name="PAYPAL-TRANSMISSION-TIME",in=io.swagger.v3.oas.annotations.enums.ParameterIn.HEADER,required=true)})
    @PostMapping(value="/api/v1/webhooks/payments",consumes="application/json")
    public BillingService.WebhookView webhook(HttpServletRequest request)throws java.io.IOException{
        byte[] bytes=request.getInputStream().readNBytes(262145);if(bytes.length>262144)throw new BillingFailure(Code.INVALID_WEBHOOK);
        String body=new String(bytes,StandardCharsets.UTF_8);Map<String,Object> payload;
        try{payload=json.readValue(body,new TypeReference<Map<String,Object>>(){});}catch(Exception e){throw new BillingFailure(Code.INVALID_WEBHOOK);}
        if(payload==null)throw new BillingFailure(Code.INVALID_WEBHOOK);
        return service.webhook(new Signature(request.getHeader("PAYPAL-AUTH-ALGO"),request.getHeader("PAYPAL-CERT-URL"),
            request.getHeader("PAYPAL-TRANSMISSION-ID"),request.getHeader("PAYPAL-TRANSMISSION-SIG"),request.getHeader("PAYPAL-TRANSMISSION-TIME")),payload,body);
    }
}
