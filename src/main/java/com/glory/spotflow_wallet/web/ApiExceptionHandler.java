package com.glory.spotflow_wallet.web;

import com.glory.spotflow_wallet.domain.wallet.InsufficientBalanceException;
import com.glory.spotflow_wallet.spotflow.exception.SpotflowApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorBody> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorBody(ex.getMessage()));
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorBody> handleInsufficientBalance(InsufficientBalanceException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorBody(ex.getMessage()));
    }

    @ExceptionHandler(SpotflowApiException.class)
    public ResponseEntity<ErrorBody> handleSpotflowError(SpotflowApiException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ErrorBody(ex.getMessage()));
    }

    public record ErrorBody(String message) {
    }
}
