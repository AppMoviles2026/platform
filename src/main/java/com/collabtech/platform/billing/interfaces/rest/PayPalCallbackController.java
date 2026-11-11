package com.collabtech.platform.billing.interfaces.rest;

import com.collabtech.platform.billing.application.services.BillingService;
import com.collabtech.platform.billing.application.services.BillingService.ReturnView;
import com.collabtech.platform.billing.domain.model.BillingModels.Purpose;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.context.annotation.Profile;

@RestController @Profile("!skeleton") @RequestMapping("/api/v1/billing/paypal")
public class PayPalCallbackController {
    private final BillingService service;
    public PayPalCallbackController(BillingService service){this.service=service;}
    @GetMapping("/callback") public ResponseEntity<?> funding(@RequestParam String state,@RequestParam(defaultValue="false")boolean cancelled){
        return response(service.callback(Purpose.PAYMENT,state,null,cancelled));
    }
    @GetMapping("/identity-callback") public ResponseEntity<?> identity(@RequestParam String state,@RequestParam(required=false)String code,
                                                                       @RequestParam(required=false)String error){
        return response(service.callback(Purpose.PAYOUT,state,code,error!=null));
    }
    private static ResponseEntity<?> response(ReturnView result){
        if(result.androidReturnUrl()!=null)return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION,result.androidReturnUrl())
            .header("Referrer-Policy","no-referrer").cacheControl(CacheControl.noStore()).build();
        return ResponseEntity.ok().header("Referrer-Policy","no-referrer").cacheControl(CacheControl.noStore()).body(result);
    }
}
