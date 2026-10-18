package com.petitesannonces.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Reconstruit l'identité de l'appelant à partir des headers posés par la gateway Kong.
 * <p>
 * Le token JWT n'arrive jamais jusqu'ici : Kong l'a validé puis retiré, et transmet
 * {@code X_USER_ID} (claim sub) et {@code X_ROLE_ID} (rôles séparés par des virgules).
 * Le backend fait confiance à ces headers car il n'est joignable que par la gateway
 * (réseau Docker internal, aucun port publié).
 * <p>
 * Headers absents ou vides : aucune authentification n'est posée, la requête reste
 * anonyme et Spring Security répond 401 sur les routes protégées.
 */
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    public static final String USER_ID_HEADER = "X_USER_ID";
    public static final String ROLES_HEADER = "X_ROLE_ID";

    /** Seuls les rôles métier sont retenus (défense en profondeur, Kong filtre déjà). */
    static final Set<String> ROLES_CONNUS = Set.of("USER", "MODERATEUR", "ADMIN");

    private static final Logger log = LoggerFactory.getLogger(HeaderAuthenticationFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String userId = trimToNull(request.getHeader(USER_ID_HEADER));
        String roles = trimToNull(request.getHeader(ROLES_HEADER));

        // GW-08 : trace ce que le backend reçoit réellement (le token lui-même n'est jamais journalisé)
        log.debug("{} {} - X_USER_ID={}, X_ROLE_ID={}, Authorization={}",
                request.getMethod(), request.getRequestURI(), userId, roles,
                request.getHeader(HttpHeaders.AUTHORIZATION) == null ? "absent" : "PRESENT");

        try {
            if (userId != null && roles != null) {
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(new PreAuthenticatedAuthenticationToken(userId, null, autorites(roles)));
                SecurityContextHolder.setContext(context);
            }
            chain.doFilter(request, response);
        } finally {
            // BK-05 : le thread retourne au pool de Tomcat, il ne doit rien garder de cet utilisateur
            SecurityContextHolder.clearContext();
        }
    }

    /** "MODERATEUR,USER" -> [ROLE_MODERATEUR, ROLE_USER] ; les rôles inconnus sont ignorés. */
    static List<GrantedAuthority> autorites(String roles) {
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(ROLES_CONNUS::contains)
                .distinct()
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
