package com.petitesannonces.annonce;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Contrôle de propriété utilisé dans les expressions de sécurité :
 * {@code @PreAuthorize("@annonceSecurity.estAuteur(#id, authentication)")}.
 */
@Component("annonceSecurity")
public class AnnonceSecurity {

    private final AnnonceRepository annonces;

    public AnnonceSecurity(AnnonceRepository annonces) {
        this.annonces = annonces;
    }

    /**
     * Vrai si l'utilisateur courant est l'auteur de l'annonce.
     * Une annonce inexistante n'est pas refusée ici : la méthode s'exécute et le service
     * répond 404, plus juste qu'un 403 sur une ressource qui n'existe pas.
     */
    @Transactional(readOnly = true)
    public boolean estAuteur(UUID id, Authentication authentication) {
        if (id == null || authentication == null) {
            return false;
        }
        return !annonces.existsById(id) || annonces.existsByIdAndAuteurId(id, authentication.getName());
    }
}
