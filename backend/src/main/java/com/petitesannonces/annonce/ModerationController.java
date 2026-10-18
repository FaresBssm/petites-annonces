package com.petitesannonces.annonce;

import com.petitesannonces.annonce.AnnonceDtos.AnnonceResponse;
import com.petitesannonces.annonce.AnnonceDtos.RejetRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Modération. La règle est posée une fois sur la classe et vaut pour toutes les méthodes :
 * aucune ne peut être ajoutée en oubliant le contrôle. ADMIN y accède car Keycloak lui
 * attribue aussi MODERATEUR (rôle composite).
 */
@RestController
@RequestMapping("/api/moderation/annonces")
@PreAuthorize("hasRole('MODERATEUR')")
public class ModerationController {

    private final AnnonceService service;

    public ModerationController(AnnonceService service) {
        this.service = service;
    }

    /** Annonces EN_ATTENTE, plus anciennes d'abord. */
    @GetMapping
    public List<AnnonceResponse> enAttente() {
        return service.listerEnAttente().stream().map(AnnonceResponse::depuis).toList();
    }

    @PatchMapping("/{id}/publier")
    public AnnonceResponse publier(@PathVariable UUID id) {
        return AnnonceResponse.depuis(service.publier(id));
    }

    @PatchMapping("/{id}/rejeter")
    public AnnonceResponse rejeter(@PathVariable UUID id, @Valid @RequestBody RejetRequest requete) {
        return AnnonceResponse.depuis(service.rejeter(id, requete.motif()));
    }
}
