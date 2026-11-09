package com.collabtech.platform.collaboration.interfaces.rest;
import com.collabtech.platform.collaboration.domain.exceptions.CollaborationFailure;
import com.collabtech.platform.shared.interfaces.rest.ApiError;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes={CollaborationController.class,DeliveryController.class})
public class CollaborationExceptionHandler {
    @ExceptionHandler(CollaborationFailure.class)
    public ResponseEntity<ApiError> failure(CollaborationFailure error){
        var status=switch(error.code()){case NOT_FOUND->HttpStatus.NOT_FOUND;case FORBIDDEN,ACCOUNT_NOT_ACTIVE,ASSET_NOT_AVAILABLE->HttpStatus.FORBIDDEN;case UNSUPPORTED_FILE->HttpStatus.UNSUPPORTED_MEDIA_TYPE;case FILE_TOO_LARGE->HttpStatus.PAYLOAD_TOO_LARGE;default->HttpStatus.CONFLICT;};
        String message=switch(error.code()){
            case NOT_FOUND->"El recurso solicitado no existe.";case FORBIDDEN->"No participas en este acuerdo o colaboración.";
            case ACCOUNT_NOT_ACTIVE->"Ambos participantes deben mantener una cuenta activa.";case SOURCE_NOT_SELECTED->"La postulación debe estar seleccionada.";
            case TERMS_EXPIRED->"Venció el plazo para aceptar estas condiciones.";case TERMS_CHANGED->"La versión de condiciones no coincide. Vuelve a consultar el acuerdo.";
            case AGREEMENT_CLOSED->"El acuerdo ya tiene una decisión definitiva.";case DECISION_ALREADY_RECORDED->"Tu decisión ya fue registrada.";
            case AGREEMENT_NOT_ACCEPTED->"Ambas partes deben aceptar las condiciones antes de iniciar la colaboración.";
            case COLLABORATION_CLOSED->"La colaboración no permite modificar entregas.";
            case QUANTITY_EXCEEDED->"Ya se registraron todas las piezas de esta obligación. Corrige una entrega rechazada.";
            case STALE_SUBMISSION->"La revisión no corresponde a la versión actual de la entrega.";
            case DELIVERABLE_ALREADY_REVIEWED->"Esta versión ya tiene una decisión.";
            case REVISION_NOT_ALLOWED->"Solo se puede corregir una entrega rechazada.";
            case CORRECTION_WINDOW_CLOSED->"Venció el plazo de corrección indicado por la empresa.";
            case IDEMPOTENCY_CONFLICT->"La clave de reintento ya fue utilizada con otra solicitud.";
            case ASSET_NOT_AVAILABLE->"El archivo no está disponible para esta entrega.";
            case UNSUPPORTED_FILE->"Usa PDF, PNG, JPEG, WebP o MP4 con un formato y extensión compatibles.";
            case FILE_TOO_LARGE->"El archivo supera el tamaño permitido.";
            case CONCURRENT_UPDATE->"El recurso cambió. Vuelve a consultarlo.";case INCOMPLETE_COLLABORATION->"Existen obligaciones pendientes.";};
        return ResponseEntity.status(status).body(new ApiError(error.code().name(),message,Map.of()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError validation(MethodArgumentNotValidException error){var fields=new LinkedHashMap<String,String>();error.getBindingResult().getFieldErrors().forEach(f->fields.putIfAbsent(f.getField(),f.getDefaultMessage()));return new ApiError("VALIDATION_ERROR","Revisa los campos enviados.",fields);}
    @ExceptionHandler({IllegalArgumentException.class,HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,org.springframework.web.bind.ServletRequestBindingException.class,org.springframework.web.multipart.support.MissingServletRequestPartException.class}) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError invalid(){return new ApiError("INVALID_REQUEST","La solicitud no es válida.",Map.of());}
    @ExceptionHandler({org.springframework.orm.ObjectOptimisticLockingFailureException.class,jakarta.persistence.OptimisticLockException.class,org.springframework.dao.PessimisticLockingFailureException.class,org.springframework.dao.DataIntegrityViolationException.class}) @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError concurrent(){return new ApiError("CONCURRENT_UPDATE","El recurso cambió. Vuelve a consultarlo.",Map.of());}
}
