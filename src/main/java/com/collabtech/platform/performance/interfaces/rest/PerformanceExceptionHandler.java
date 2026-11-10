package com.collabtech.platform.performance.interfaces.rest;
import com.collabtech.platform.performance.domain.exceptions.PerformanceFailure;
import com.collabtech.platform.shared.interfaces.rest.ApiError;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes=PerformanceController.class)
public class PerformanceExceptionHandler {
    @ExceptionHandler(PerformanceFailure.class)
    public ResponseEntity<ApiError> failure(PerformanceFailure error) {
        var status=switch(error.code()){
            case NOT_FOUND->HttpStatus.NOT_FOUND;
            case FORBIDDEN,ACCOUNT_NOT_ACTIVE,ASSET_NOT_AVAILABLE->HttpStatus.FORBIDDEN;
            case FILE_TOO_LARGE->HttpStatus.PAYLOAD_TOO_LARGE;case UNSUPPORTED_FILE->HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            default->HttpStatus.CONFLICT;
        };
        String message=switch(error.code()){
            case NOT_FOUND->"El recurso solicitado no existe.";case FORBIDDEN->"No tienes acceso a este recurso.";
            case ACCOUNT_NOT_ACTIVE->"Se requiere una cuenta activa.";case COLLABORATION_CLOSED->"La colaboración cancelada no admite nuevos registros de resultados.";
            case SOURCE_NOT_CURRENT->"Selecciona una versión actual de entrega que no esté rechazada.";
            case SOURCE_NOT_PUBLISHED->"La entrega debe incluir un enlace publicado para obtener métricas automáticas.";
            case POST_URL_MISMATCH->"El identificador no coincide con el enlace canónico de la publicación.";
            case IDEMPOTENCY_CONFLICT->"La clave de reintento ya se utilizó con otra solicitud.";case CONCURRENT_UPDATE->"La evidencia cambió. Vuelve a consultarla.";
            case EVIDENCE_ALREADY_REVIEWED->"La evidencia ya tiene una revisión.";case ASSET_NOT_AVAILABLE->"El archivo no está disponible para esta evidencia.";
            case UNSUPPORTED_FILE->"Usa PDF, PNG, JPEG, WebP o MP4 con extensión y formato compatibles.";case FILE_TOO_LARGE->"El archivo supera el tamaño permitido.";
        };
        return ResponseEntity.status(status).body(new ApiError(error.code().name(),message,Map.of()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError validation(MethodArgumentNotValidException error) {
        var fields=new LinkedHashMap<String,String>();error.getBindingResult().getFieldErrors().forEach(f->fields.putIfAbsent(f.getField(),f.getDefaultMessage()));
        return new ApiError("VALIDATION_ERROR","Revisa los campos enviados.",fields);
    }
    @ExceptionHandler({IllegalArgumentException.class,HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,
        org.springframework.web.bind.ServletRequestBindingException.class,org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError invalid(){return new ApiError("INVALID_REQUEST","La solicitud no es válida.",Map.of());}
    @ExceptionHandler({org.springframework.dao.DataIntegrityViolationException.class,org.springframework.dao.PessimisticLockingFailureException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError concurrent(){return new ApiError("CONCURRENT_UPDATE","El recurso cambió. Vuelve a consultarlo.",Map.of());}
}
