package com.petitesannonces.security;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * Sécurité stateless : l'identité est relue à chaque requête depuis les headers de la gateway.
 * Les règles par rôle sont posées par {@code @PreAuthorize} sur les méthodes (BK-04),
 * d'où {@code @EnableMethodSecurity}.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProblemHandler problemHandler,
                                            @Value("${petitesannonces.gateway-secret:}") String gatewaySecret)
            throws Exception {
        HeaderAuthenticationFilter headerFilter = new HeaderAuthenticationFilter();
        return http
                // Pas de cookie de session, donc rien à protéger contre le CSRF
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Avant le filtre "anonyme" : s'il n'a rien posé, la requête devient anonyme
                .addFilterBefore(headerFilter, AnonymousAuthenticationFilter.class)
                // Bonus : provenance vérifiée AVANT de faire confiance aux headers d'identité
                .addFilterBefore(new GatewaySecretFilter(gatewaySecret, problemHandler), HeaderAuthenticationFilter.class)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .authorizeHttpRequests(auth -> auth
                        // Le forward interne vers /error ne doit pas être re-contrôlé (sinon tout devient 401)
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .build();
    }
}
