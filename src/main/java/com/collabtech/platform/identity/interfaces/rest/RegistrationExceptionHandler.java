package com.collabtech.platform.identity.interfaces.rest;

import com.collabtech.platform.identity.domain.exceptions.DuplicateEmailException;
import com.collabtech.platform.shared.interfaces.rest.ApiError;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.ResponseEntity;
import com.collabtech.platform.identity.application.exceptions.IdentityFailure;
import com.collabtech.platform.identity.domain.exceptions.DuplicateSocialAccountException;

@RestControllerAdvice(assignableTypes = {AuthController.class, UserProfileController.class, SocialMediaController.class})
public class RegistrationExceptionHandler {
    @ExceptionHandler(DuplicateEmailException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError duplicateEmail() {
        return new ApiError("EMAIL_ALREADY_REGISTERED", "El correo ya está registrado.",
                Map.of("email", "Utiliza otro correo o inicia sesión."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError invalidFields(MethodArgumentNotValidException exception) {
        var fields = new LinkedHashMap<String, String>();
        exception.getBindingResult().getFieldErrors().forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return new ApiError("VALIDATION_ERROR", "Revisa los campos enviados.", fields);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError invalidRequest() {
        return new ApiError("INVALID_REQUEST", "La solicitud no es válida.", Map.of());
    }

    @ExceptionHandler(IdentityFailure.class)
    public ResponseEntity<ApiError> identityFailure(IdentityFailure failure) {
        HttpStatus status = switch (failure.code()) {
            case INVALID_CREDENTIALS -> HttpStatus.UNAUTHORIZED;
            case CREATOR_REQUIRED, ACCOUNT_NOT_ACTIVE, AUTHORIZATION_DENIED -> HttpStatus.FORBIDDEN;
            case INVALID_RECOVERY_TOKEN, INVALID_OAUTH_STATE -> HttpStatus.BAD_REQUEST;
            case AUTHORIZATION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case PROVIDER_NOT_CONFIGURED -> HttpStatus.SERVICE_UNAVAILABLE;
            case PROVIDER_FAILED -> HttpStatus.BAD_GATEWAY;
        };
        String message = switch (failure.code()) {
            case INVALID_CREDENTIALS -> "Credenciales inválidas.";
            case CREATOR_REQUIRED -> "Esta operación requiere una cuenta de creador.";
            case ACCOUNT_NOT_ACTIVE -> "La cuenta no está habilitada.";
            case INVALID_RECOVERY_TOKEN -> "El enlace de recuperación no es válido o ha vencido.";
            case INVALID_OAUTH_STATE -> "La autorización no es válida o ha vencido.";
            case AUTHORIZATION_NOT_FOUND -> "La autorización no existe o no te pertenece.";
            case AUTHORIZATION_DENIED -> "No se concedió la autorización necesaria.";
            case PROVIDER_NOT_CONFIGURED -> "El proveedor de redes sociales aún no está configurado.";
            case PROVIDER_FAILED -> "No se pudo verificar la autorización con el proveedor.";
        };
        return ResponseEntity.status(status).body(new ApiError(failure.code().name(), message, Map.of()));
    }

    @ExceptionHandler(DuplicateSocialAccountException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError duplicateSocial() {
        return new ApiError("SOCIAL_ACCOUNT_ALREADY_LINKED", "La cuenta social ya está vinculada a tu perfil.", Map.of());
    }

    @ExceptionHandler({org.springframework.orm.ObjectOptimisticLockingFailureException.class, jakarta.persistence.OptimisticLockException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError concurrentUpdate() {
        return new ApiError("CONCURRENT_UPDATE", "La información cambió. Vuelve a consultarla antes de guardar.", Map.of());
    }
}
