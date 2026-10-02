package com.campusfind.controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.*;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.*;
@RestControllerAdvice(annotations=RestController.class)
public class ApiExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<?> status(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",Objects.toString(e.getReason(),"This action is unavailable")));}
    @ExceptionHandler(MethodArgumentNotValidException.class) public ResponseEntity<?> validation(MethodArgumentNotValidException e){return ResponseEntity.badRequest().body(Map.of("message",e.getBindingResult().getFieldErrors().stream().map(f->f.getField()+": "+f.getDefaultMessage()).findFirst().orElse("Check the form fields")));}
    @ExceptionHandler({IllegalArgumentException.class,org.springframework.http.converter.HttpMessageNotReadableException.class}) public ResponseEntity<?> invalid(Exception e){return ResponseEntity.badRequest().body(Map.of("message",e instanceof IllegalArgumentException?Objects.toString(e.getMessage(),"Check the form fields"):"Enter valid form values"));}
    @ExceptionHandler(NoSuchElementException.class) public ResponseEntity<?> missing(){return ResponseEntity.status(404).body(Map.of("message","The requested record was not found"));}
    @ExceptionHandler(MaxUploadSizeExceededException.class) public ResponseEntity<?> size(){return ResponseEntity.status(413).body(Map.of("message","Choose an image smaller than 5 MB"));}
    @ExceptionHandler(DataIntegrityViolationException.class) public ResponseEntity<?> duplicate(){return ResponseEntity.status(409).body(Map.of("message","This record already exists or conflicts with another action. Refresh and try again."));}
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class) public ResponseEntity<?> denied(){return ResponseEntity.status(403).body(Map.of("message","You do not have permission for this action"));}
    @ExceptionHandler(Exception.class) public ResponseEntity<?> other(Exception e){org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class).error("Request failed: {}",e.getClass().getSimpleName(),e);return ResponseEntity.status(500).body(Map.of("message","The request could not be completed. Please try again or contact campus administration."));}
}
