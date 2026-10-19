package com.petitesannonces.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Bonus : le backend refuse tout appel /api qui ne vient pas de la gateway.
 * <p>
 * Kong (plugin identity-headers) pose X-Gateway-Secret ; sans ce secret, un autre conteneur du réseau
 * internal pourrait envoyer de faux X_USER_ID / X_ROLE_ID. Exécuté AVANT HeaderAuthenticationFilter.
 */
public class GatewaySecretFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Gateway-Secret";

    private static final Logger log = LoggerFactory.getLogger(GatewaySecretFilter.class);

    private final byte[] secretAttendu;
    private final SecurityProblemHandler problemHandler;

    public GatewaySecretFilter(String secret, SecurityProblemHandler problemHandler) {
        this.secretAttendu = secret == null || secret.isBlank() ? null : secret.getBytes(StandardCharsets.UTF_8);
        this.problemHandler = problemHandler;
        if (this.secretAttendu == null) {
            log.warn("GATEWAY_SECRET non défini : la vérification de provenance (X-Gateway-Secret) est DÉSACTIVÉE");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Healthcheck Docker : appelé depuis le conteneur lui-même, jamais via la gateway
        return secretAttendu == null || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String recu = request.getHeader(HEADER);
        // Comparaison en temps constant : la durée de la réponse ne révèle rien du secret
        if (recu == null || !MessageDigest.isEqual(secretAttendu, recu.getBytes(StandardCharsets.UTF_8))) {
            problemHandler.refuser(request, response, HttpStatus.FORBIDDEN,
                    "Appel refusé : la requête ne provient pas de la gateway.");
            return;
        }
        chain.doFilter(request, response);
    }
}
