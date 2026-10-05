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

@RestControllerAdvice(assignableTypes = AuthController.class)
public class RegistrationExceptionHandler {
    @ExceptionHandler(DuplicateEmailException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError duplicateEmail() {
        return new ApiError("EMAIL_ALREADY_REGISTERED", "El correo ya está registrado.",
                Map.of("email", "Utiliza otro correo o inicia sesión cuando esa funcionalidad esté disponible."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError invalidFields(MethodArgumentNotValidException exception) {
        var fields = new LinkedHashMap<String, String>();
        exception.getBindingResult().getFieldErrors().forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return new ApiError("VALIDATION_ERROR", "Revisa los campos del registro.", fields);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError invalidRequest() {
        return new ApiError("INVALID_REGISTRATION_REQUEST", "La solicitud de registro no es válida.", Map.of());
    }
}
