package com.petitesannonces.annonce;

import com.petitesannonces.annonce.AnnonceDtos.AnnonceRequest;
import com.petitesannonces.annonce.AnnonceDtos.AnnonceResponse;
import com.petitesannonces.annonce.AnnonceDtos.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authorization.method.HandleAuthorizationDenied;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Annonces côté auteur. Chaque règle d'accès du §3 de l'énoncé est une annotation (BK-04).
 */
@RestController
@RequestMapping("/api/annonces")
public class AnnonceController {

    private final AnnonceService service;

    public AnnonceController(AnnonceService service) {
        this.service = service;
    }

    /** Annonces publiées, les plus récentes d'abord, paginées (size ≤ 50), filtre catégorie optionnel. */
    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public PageResponse<AnnonceResponse> listerPubliees(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "doit être positif ou nul") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "doit valoir au moins 1")
            @Max(value = 50, message = "ne doit pas dépasser 50") int size,
            @RequestParam(required = false) Long categorieId) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "dateCreation"));
        return PageResponse.depuis(service.listerPubliees(categorieId, pageable), AnnonceResponse::depuis);
    }

    @GetMapping("/mes-annonces")
    @PreAuthorize("hasRole('USER')")
    public List<AnnonceResponse> mesAnnonces(Authentication authentication) {
        return service.listerParAuteur(authentication.getName()).stream().map(AnnonceResponse::depuis).toList();
    }

    /**
     * Visible si publiée, ou si l'appelant est l'auteur ou MODERATEUR. Sinon 404 (MT-02) :
     * le refus de @PostAuthorize est converti par AnnonceInvisibleHandler.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    @PostAuthorize("returnObject.statut() == T(com.petitesannonces.annonce.Statut).PUBLIEE"
            + " or returnObject.auteurId() == authentication.name"
            + " or hasRole('MODERATEUR')")
    @HandleAuthorizationDenied(handlerClass = AnnonceInvisibleHandler.class)
    public AnnonceResponse trouver(@PathVariable UUID id) {
        return AnnonceResponse.depuis(service.trouver(id));
    }

    /** Création en EN_ATTENTE ; auteur = X_USER_ID (MT-01) ; 201 + Location. */
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<AnnonceResponse> creer(@Valid @RequestBody AnnonceRequest requete,
                                                 Authentication authentication) {
        Annonce annonce = service.creer(requete, authentication.getName());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(annonce.getId()).toUri();
        return ResponseEntity.created(location).body(AnnonceResponse.depuis(annonce));
    }

    /** Réservé à l'auteur (MT-03) ; l'annonce repasse EN_ATTENTE (MT-04). */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('USER') and @annonceSecurity.estAuteur(#id, authentication)")
    public AnnonceResponse modifier(@PathVariable UUID id, @Valid @RequestBody AnnonceRequest requete) {
        return AnnonceResponse.depuis(service.modifier(id, requete));
    }

    /** Auteur, MODERATEUR ou ADMIN (MT-03). */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('MODERATEUR', 'ADMIN') or @annonceSecurity.estAuteur(#id, authentication)")
    public void supprimer(@PathVariable UUID id) {
        service.supprimer(id);
    }
}
