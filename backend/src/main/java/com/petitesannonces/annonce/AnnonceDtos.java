package com.petitesannonces.annonce;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public final class AnnonceDtos {

    private AnnonceDtos() {
    }

    /**
     * Corps de POST et PUT. Volontairement sans auteurId ni statut : un client qui les envoie
     * est ignoré (MT-01), l'auteur vient de X_USER_ID et le statut du cycle de vie.
     */
    public record AnnonceRequest(
            @NotBlank(message = "est obligatoire")
            @Size(min = 5, max = 100, message = "doit contenir entre 5 et 100 caractères")
            String titre,

            @Size(max = 2000, message = "ne doit pas dépasser 2000 caractères")
            String description,

            @NotNull(message = "est obligatoire")
            @DecimalMin(value = "0.0", message = "doit être positif ou nul")
            @Digits(integer = 10, fraction = 2, message = "doit avoir au plus 10 chiffres avant la virgule et 2 après")
            BigDecimal prix,

            @NotNull(message = "est obligatoire")
            Long categorieId) {

        public AnnonceRequest {
            titre = titre == null ? null : titre.strip();
        }
    }

    public record AnnonceResponse(
            UUID id,
            String titre,
            String description,
            BigDecimal prix,
            Long categorieId,
            Statut statut,
            String auteurId,
            String motifRejet,
            Instant dateCreation,
            Instant dateModification) {

        public static AnnonceResponse depuis(Annonce a) {
            return new AnnonceResponse(a.getId(), a.getTitre(), a.getDescription(), a.getPrix(),
                    a.getCategorieId(), a.getStatut(), a.getAuteurId(), a.getMotifRejet(),
                    a.getDateCreation(), a.getDateModification());
        }
    }

    /** Page stable côté JSON (la sérialisation directe de Page de Spring Data est déconseillée). */
    public record PageResponse<T>(List<T> contenu, int page, int size, long totalElements, int totalPages) {

        public static <E, T> PageResponse<T> depuis(Page<E> page, Function<E, T> conversion) {
            return new PageResponse<>(page.getContent().stream().map(conversion).toList(),
                    page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }
}
