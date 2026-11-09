package com.collabtech.platform.collaboration.interfaces.rest;

import com.collabtech.platform.shared.interfaces.rest.ApiError;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** Multipart parsing may fail before a controller is selected. */
@RestControllerAdvice
public class DeliveryUploadExceptionHandler {
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ApiError tooLarge() { return new ApiError("FILE_TOO_LARGE","El archivo supera el tamaño permitido.",Map.of()); }
}
