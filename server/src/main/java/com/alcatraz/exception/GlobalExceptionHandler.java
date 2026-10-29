package com.alcatraz.exception;

import org.apache.coyote.BadRequestException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 404 - Not Found
    @ExceptionHandler(GameNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleGameNotFoundException(GameNotFoundException ex, WebRequest request) {
        logger.warn("GameNotFoundException: {} - Request: {}", ex.getMessage(), request.getDescription(false));
        return buildResponseEntity("Game not found", HttpStatus.NOT_FOUND);
    }

    // 409 - Conflict (name already in use for this game)
    @ExceptionHandler(PlayerNameTakenException.class)
    public ResponseEntity<Map<String, Object>> handlePlayerNameTakenException(PlayerNameTakenException ex, WebRequest request) {
        logger.warn("PlayerNameTakenException: {} - Request: {}", ex.getMessage(), request.getDescription(false));
        return buildResponseEntity("Player name already taken. " + ex.getMessage(), HttpStatus.CONFLICT);
    }

    // 409 - Conflict (registration closed)
    @ExceptionHandler(GameAlreadyStartedException.class)
    public ResponseEntity<Map<String, Object>> handleGameAlreadyStartedException(GameAlreadyStartedException ex, WebRequest request) {
        logger.warn("GameAlreadyStartedException: {} - Request: {}", ex.getMessage(), request.getDescription(false));
        return buildResponseEntity("Game already started. " + ex.getMessage(), HttpStatus.CONFLICT);
    }

    // 400 - Bad Request (not enough players to start)
    @ExceptionHandler(NotEnoughPlayersException.class)
    public ResponseEntity<Map<String, Object>> handleNotEnoughPlayersException(NotEnoughPlayersException ex, WebRequest request) {
        logger.warn("NotEnoughPlayersException: {} - Request: {}", ex.getMessage(), request.getDescription(false));
        return buildResponseEntity("Not enough players. " + ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    // 503 - Service Unavailable (this node isn't primary, client should fail over)
    @ExceptionHandler(NotPrimaryException.class)
    public ResponseEntity<Map<String, Object>> handleNotPrimaryException(NotPrimaryException ex, WebRequest request) {
        logger.warn("NotPrimaryException: {} - Request: {}", ex.getMessage(), request.getDescription(false));
        return buildResponseEntity("Not primary. " + ex.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler({InvalidRequestException.class, BadRequestException.class,
            HttpMessageNotReadableException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception ex) {
        String message = ex instanceof InvalidRequestException ? ex.getMessage() : "Invalid request";
        return buildResponseEntity(message, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MissingTokenException.class)
    public ResponseEntity<Map<String, Object>> handleMissingToken(MissingTokenException ex) {
        var response = buildResponseEntity(ex.getMessage(), HttpStatus.UNAUTHORIZED);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer").body(response.getBody());
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidToken(InvalidTokenException ex) {
        return buildResponseEntity(ex.getMessage(), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(GameFullException.class)
    public ResponseEntity<Map<String, Object>> handleGameFull(GameFullException ex) {
        return buildResponseEntity(ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(ReplicationUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleReplicationFailure(ReplicationUnavailableException ex) {
        logger.warn("Replication outcome uncertain: {}", ex.getMessage());
        return buildResponseEntity(ex.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
    }

    // 500 - Internal Server Error
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGlobalException(Exception ex, WebRequest request) {
        logger.error("Unhandled exception for request {}", request.getDescription(false), ex);
        return buildResponseEntity("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<Map<String, Object>> buildResponseEntity(String message, HttpStatus status) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", message);
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        return new ResponseEntity<>(body, status);
    }
}
