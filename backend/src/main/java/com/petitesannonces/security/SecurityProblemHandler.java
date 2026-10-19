package com.petitesannonces.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;

/**
 * Réponses 401 / 403 de Spring Security au format ProblemDetail (RFC 9457),
 * au lieu des pages d'erreur par défaut.
 */
@Component
public class SecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public SecurityProblemHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 401 : aucune identité (headers de la gateway absents ou vides). */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        ecrire(request, response, HttpStatus.UNAUTHORIZED,
                "Identité absente : les headers X_USER_ID et X_ROLE_ID posés par la gateway sont requis.");
    }

    /** 403 : identité connue, mais droits insuffisants. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        ecrire(request, response, HttpStatus.FORBIDDEN, "Accès refusé : droits insuffisants pour cette opération.");
    }

    /** Refus émis directement par un filtre (ex. GatewaySecretFilter). */
    public void refuser(HttpServletRequest request, HttpServletResponse response,
                        HttpStatus status, String detail) throws IOException {
        ecrire(request, response, status, detail);
    }

    private void ecrire(HttpServletRequest request, HttpServletResponse response,
                        HttpStatus status, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
