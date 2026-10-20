package com.p2pfileshare.tracker.config;

import com.p2pfileshare.tracker.service.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex,HttpServletRequest req){
        var fields=ex.getBindingResult().getFieldErrors().stream().map(e->new ApiError.FieldError(e.getField(),e.getDefaultMessage())).toList();
        return response(HttpStatus.BAD_REQUEST,"Validation failed",req,fields);
    }
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> constraint(ConstraintViolationException ex,HttpServletRequest req){return response(HttpStatus.BAD_REQUEST,"Validation failed",req,List.of());}
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException ex,HttpServletRequest req){return response(ex.getStatus(),ex.getMessage(),req,List.of());}
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> integrity(DataIntegrityViolationException ex,HttpServletRequest req){return response(HttpStatus.CONFLICT,"Resource conflicts with an existing record",req,List.of());}
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> other(Exception ex,HttpServletRequest req){return response(HttpStatus.INTERNAL_SERVER_ERROR,"Internal server error",req,List.of());}
    private ResponseEntity<ApiError> response(HttpStatus status,String message,HttpServletRequest req,List<ApiError.FieldError> fields){
        return ResponseEntity.status(status).body(new ApiError(Instant.now(),status.value(),status.getReasonPhrase(),message,req.getRequestURI(),fields));
    }
}
