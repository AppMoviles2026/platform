package com.collabtech.platform.campaign.interfaces.rest;

import com.collabtech.platform.campaign.domain.exceptions.CampaignFailure;
import com.collabtech.platform.shared.interfaces.rest.ApiError;
import java.util.Map;
import java.util.LinkedHashMap;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes={CampaignController.class, ApplicationController.class})
public class CampaignExceptionHandler {
    @ExceptionHandler(CampaignFailure.class)
    public ResponseEntity<ApiError> business(CampaignFailure failure) {
        HttpStatus status = switch (failure.code()) {
            case INVALID_CONDITIONS, INCOMPLETE_CAMPAIGN -> HttpStatus.UNPROCESSABLE_ENTITY;
            case CAMPAIGN_NOT_DRAFT, CAMPAIGN_NOT_OPEN, CAMPAIGN_HAS_APPLICATIONS, IDEMPOTENCY_KEY_REUSED, CAMPAIGN_NOT_ACCEPTING_APPLICATIONS, APPLICATION_ALREADY_EXISTS, APPLICATION_NOT_PENDING, CONCURRENT_UPDATE -> HttpStatus.CONFLICT;
            case CAMPAIGN_NOT_FOUND, APPLICATION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_CONFIRMATION -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.FORBIDDEN;
        };
        String message = switch (failure.code()) {
            case INVALID_CONDITIONS -> "Las condiciones son incompatibles: revisa requisitos, entregables, fechas y compensación.";
            case INCOMPLETE_CAMPAIGN -> "Completa las condiciones antes de publicar.";
            case CAMPAIGN_NOT_DRAFT -> "Solo se permite preparar o publicar una campaña en borrador.";
            case CAMPAIGN_NOT_OPEN -> "Solo puedes cerrar postulaciones de una campaña publicada.";
            case CAMPAIGN_HAS_APPLICATIONS -> "No puedes descartar una campaña con postulaciones.";
            case IDEMPOTENCY_KEY_REUSED -> "La clave de reintento ya se utilizó con otra solicitud.";
            case CAMPAIGN_NOT_FOUND -> "La campaña no existe.";
            case BRAND_REQUIRED -> "Esta operación requiere una empresa.";
            case CREATOR_REQUIRED -> "Esta operación requiere un creador.";
            case ACCOUNT_NOT_ACTIVE -> "La cuenta no está habilitada.";
            case FORBIDDEN -> "No tienes permiso para acceder o modificar este recurso.";
            case CAMPAIGN_NOT_ACCEPTING_APPLICATIONS -> "La campaña no acepta postulaciones.";
            case APPLICATION_ALREADY_EXISTS -> "Ya postulaste a esta campaña.";
            case APPLICATION_NOT_FOUND -> "La postulación no existe o no te pertenece.";
            case APPLICATION_NOT_PENDING -> "Solo puedes editar o cancelar postulaciones pendientes.";
            case INVALID_CONFIRMATION -> "Solo puedes confirmar requisitos manuales de esta campaña.";
            case CONCURRENT_UPDATE -> "El recurso cambió. Vuelve a consultarlo antes de guardar.";
        };
        return ResponseEntity.status(status).body(new ApiError(failure.code().name(), message, Map.of()));
    }
    @ExceptionHandler(com.collabtech.platform.campaign.domain.exceptions.UnmetRequirementsException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ApiError requirements(com.collabtech.platform.campaign.domain.exceptions.UnmetRequirementsException failure) {
        var fields = new LinkedHashMap<String,String>();
        failure.requirements().forEach(item -> fields.put("requirements."+item.id().value(),item.description()));
        return new ApiError("REQUIREMENTS_NOT_MET", "No cumples los requisitos obligatorios indicados.", fields);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError validation(MethodArgumentNotValidException failure) {
        var fields = new LinkedHashMap<String,String>();
        failure.getBindingResult().getFieldErrors().forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return new ApiError("VALIDATION_ERROR", "Revisa los campos enviados.", fields);
    }
    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError invalid() { return new ApiError("INVALID_REQUEST", "La solicitud no es válida.", Map.of()); }
    @ExceptionHandler({org.springframework.orm.ObjectOptimisticLockingFailureException.class, jakarta.persistence.OptimisticLockException.class,
            org.springframework.dao.PessimisticLockingFailureException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError concurrent() { return new ApiError("CONCURRENT_UPDATE", "El recurso cambió. Vuelve a consultarlo antes de guardar.", Map.of()); }
}
