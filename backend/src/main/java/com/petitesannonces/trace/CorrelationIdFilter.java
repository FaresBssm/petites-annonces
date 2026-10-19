package com.petitesannonces.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Bonus traçabilité : reprend l'identifiant X-Correlation-ID posé par le plugin correlation-id de Kong
 * et le place dans le MDC, pour qu'il figure sur chaque ligne de log de la requête (logging.pattern.correlation).
 * <p>
 * Premier filtre de la chaîne (avant Spring Security) : même un 401 ou un 403 est tracé.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-ID";
    public static final String CLE_MDC = "correlationId";

    /** Un client peut fournir sa propre valeur : on refuse tout ce qui pourrait falsifier les logs. */
    private static final Pattern FORMAT_SUR = Pattern.compile("[A-Za-z0-9#.\\-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String valeur = request.getHeader(HEADER);
        if (valeur != null && FORMAT_SUR.matcher(valeur).matches()) {
            MDC.put(CLE_MDC, valeur);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CLE_MDC);
        }
    }
}
