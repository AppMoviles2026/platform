package com.collabtech.platform.billing.interfaces.rest;
import com.collabtech.platform.billing.application.services.SubscriptionService;
import org.springframework.web.bind.annotation.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
@RestController @Profile("!skeleton")
public class SubscriptionCallbackController {
    private final SubscriptionService service;
    public SubscriptionCallbackController(SubscriptionService service){this.service=service;}
    @GetMapping("/api/v1/billing/paypal/subscription-callback")
    public ResponseEntity<?> callback(@RequestParam String state,@RequestParam(defaultValue="false")boolean cancelled){
        var result=service.callback(state,cancelled);
        var builder=ResponseEntity.status(result.androidReturnUrl()==null?HttpStatus.OK:HttpStatus.SEE_OTHER)
            .header("Referrer-Policy","no-referrer").cacheControl(CacheControl.noStore());
        return result.androidReturnUrl()==null?builder.body(result):builder.header(HttpHeaders.LOCATION,result.androidReturnUrl()).build();
    }
}
