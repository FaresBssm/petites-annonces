package com.petitesannonces.categorie;

import com.petitesannonces.categorie.CategorieDtos.CategorieRequest;
import com.petitesannonces.categorie.CategorieDtos.CategorieResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Règles d'accès portées uniquement par @PreAuthorize (BK-04).
 * La hiérarchie (ADMIN ⊃ MODERATEUR ⊃ USER) est déjà résolue par Keycloak dans X_ROLE_ID.
 */
@RestController
@RequestMapping("/api/categories")
public class CategorieController {

    private final CategorieService service;

    public CategorieController(CategorieService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public List<CategorieResponse> lister() {
        return service.lister().stream().map(CategorieResponse::depuis).toList();
    }

    /** Pas d'en-tête Location : l'API n'expose pas de GET /api/categories/{id}. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public CategorieResponse creer(@Valid @RequestBody CategorieRequest requete) {
        return CategorieResponse.depuis(service.creer(requete.libelle()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void supprimer(@PathVariable Long id) {
        service.supprimer(id);
    }
}
