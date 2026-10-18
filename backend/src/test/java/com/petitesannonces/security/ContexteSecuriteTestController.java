package com.petitesannonces.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Contrôleur présent uniquement dans les tests (src/test) : expose le contenu brut du
 * SecurityContextHolder tel qu'un contrôleur le voit (BK-03).
 */
@RestController
class ContexteSecuriteTestController {

    @GetMapping("/api/test/contexte")
    Map<String, Object> contexte() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        List<String> autorites = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        return Map.of(
                "principal", auth.getPrincipal(),
                "autorites", autorites,
                "type", auth.getClass().getSimpleName(),
                "authentifie", auth.isAuthenticated());
    }
}
