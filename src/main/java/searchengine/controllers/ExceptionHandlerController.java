package searchengine.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import searchengine.exception.ApiServiceException;
import searchengine.model.response.IndexingErrorResponse;

@RestControllerAdvice
public class ExceptionHandlerController {

    @ExceptionHandler(ApiServiceException.class)
    public ResponseEntity<IndexingErrorResponse> indexingInProgressError(ApiServiceException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new IndexingErrorResponse(false, ex.getMessage()));
    }

}
