package com.collabtech.platform.billing.interfaces.rest;
import com.collabtech.platform.billing.application.services.CompensationService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @Profile("!skeleton")
public class CompensationCallbackController {
    private final CompensationService service;
    public CompensationCallbackController(CompensationService service){this.service=service;}
    @GetMapping("/api/v1/billing/paypal/compensation-callback")
    public ResponseEntity<?> callback(@RequestParam String state,@RequestParam(defaultValue="false")boolean cancelled){
        var result=service.callback(state,cancelled);
        var response=ResponseEntity.status(result.androidReturnUrl()==null?HttpStatus.OK:HttpStatus.SEE_OTHER).header("Referrer-Policy","no-referrer").cacheControl(CacheControl.noStore());
        return result.androidReturnUrl()==null?response.body(result):response.header(HttpHeaders.LOCATION,result.androidReturnUrl()).build();
    }
}
