package com.collabtech.platform.billing.interfaces.rest;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure;
import com.collabtech.platform.shared.interfaces.rest.ApiError;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
@RestControllerAdvice(assignableTypes={BillingController.class,PayPalCallbackController.class,PaymentsWebhookController.class})
public class BillingExceptionHandler {
    @ExceptionHandler(BillingFailure.class)
    public ResponseEntity<ApiError> failure(BillingFailure error){
        var status=switch(error.code()){
            case NOT_FOUND->HttpStatus.NOT_FOUND;
            case FORBIDDEN,ACCOUNT_NOT_ACTIVE->HttpStatus.FORBIDDEN;
            case INVALID_STATE,INVALID_WEBHOOK->HttpStatus.BAD_REQUEST;
            case SIGNATURE_INVALID->HttpStatus.UNAUTHORIZED;
            case PROVIDER_NOT_CONFIGURED,PROVIDER_UNAVAILABLE,PROVIDER_PERMISSION_REQUIRED,WEBHOOK_NOT_CONFIGURED->HttpStatus.SERVICE_UNAVAILABLE;
            case INVALID_PROVIDER_RESPONSE->HttpStatus.BAD_GATEWAY;
            default->HttpStatus.CONFLICT;
        };
        String message=switch(error.code()){
            case METHOD_REQUIRED->"Primero vincula un método financiero válido para tu rol.";
            case SIGNATURE_INVALID->"No se pudo verificar la firma del proveedor.";
            case PROVIDER_NOT_CONFIGURED,WEBHOOK_NOT_CONFIGURED->"La integración Sandbox requiere configuración del servidor.";
            case PROVIDER_PERMISSION_REQUIRED->"La aplicación de PayPal no tiene habilitado el permiso requerido.";
            case OWNERSHIP_CONFLICT->"La cuenta del proveedor ya pertenece a otro usuario.";
            case ACCOUNT_NOT_VERIFIED->"PayPal debe confirmar la cuenta y el correo electrónico.";
            case AUTHORIZATION_EXPIRED->"La autorización venció. Inicia una nueva vinculación.";
            case PROVIDER_UNAVAILABLE->"PayPal no está disponible. Consulta el estado antes de reintentar.";
            default->"No se pudo completar la solicitud de vinculación.";
        };
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(new ApiError(error.code().name(),message,Map.of()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError validation(MethodArgumentNotValidException e){var fields=new LinkedHashMap<String,String>();
        e.getBindingResult().getFieldErrors().forEach(f->fields.putIfAbsent(f.getField(),f.getDefaultMessage()));
        return new ApiError("VALIDATION_ERROR","Revisa los campos enviados.",fields);}
    @ExceptionHandler({IllegalArgumentException.class,HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,
        org.springframework.web.bind.ServletRequestBindingException.class}) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError invalid(){return new ApiError("INVALID_REQUEST","La solicitud no es válida.",Map.of());}
    @ExceptionHandler({org.springframework.dao.DataIntegrityViolationException.class,org.springframework.dao.PessimisticLockingFailureException.class})
    @ResponseStatus(HttpStatus.CONFLICT) public ApiError conflict(){return new ApiError("CONCURRENT_UPDATE","El estado cambió. Consulta el recurso antes de reintentar.",Map.of());}
}
