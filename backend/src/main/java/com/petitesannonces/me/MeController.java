package com.petitesannonces.me;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private static final String PREFIXE_ROLE = "ROLE_";

    /**
     * Identité telle que Spring Security la voit : l'{@link Authentication} injectée ici
     * est celle du SecurityContextHolder, posée par HeaderAuthenticationFilter.
     */
    @GetMapping
    public MeResponse me(Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(autorite -> autorite.startsWith(PREFIXE_ROLE))
                .map(autorite -> autorite.substring(PREFIXE_ROLE.length()))
                .sorted()
                .toList();
        return new MeResponse(authentication.getName(), roles);
    }

    public record MeResponse(String userId, List<String> roles) {
    }
}
