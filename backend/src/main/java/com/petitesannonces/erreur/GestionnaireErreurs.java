package com.petitesannonces.erreur;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;
import java.util.TreeMap;

/**
 * Erreurs applicatives au format ProblemDetail (RFC 9457).
 * <p>
 * ResponseEntityExceptionHandler couvre déjà les erreurs Spring MVC (400 corps illisible, 404, 405...).
 * Volontairement, aucun handler "attrape-tout" : les refus de @PreAuthorize doivent remonter
 * jusqu'à Spring Security, qui répond 403 via SecurityProblemHandler.
 */
@RestControllerAdvice
public class GestionnaireErreurs extends ResponseEntityExceptionHandler {

    /** 400 de Bean Validation, avec le détail par champ (MT-10). */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> erreurs = new TreeMap<>();
        for (FieldError erreur : ex.getBindingResult().getFieldErrors()) {
            erreurs.putIfAbsent(erreur.getField(), erreur.getDefaultMessage());
        }
        ProblemDetail problem = ex.getBody();
        problem.setDetail("Requête invalide : " + erreurs.size() + " champ(s) en erreur.");
        problem.setProperty("errors", erreurs);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @ExceptionHandler(ChampInvalideException.class)
    public ProblemDetail champInvalide(ChampInvalideException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Requête invalide : 1 champ(s) en erreur.");
        problem.setProperty("errors", Map.of(ex.getChamp(), ex.getMessage()));
        return problem;
    }

    @ExceptionHandler(RessourceIntrouvableException.class)
    public ProblemDetail introuvable(RessourceIntrouvableException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflitException.class)
    public ProblemDetail conflit(ConflitException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }
}
