package com.petitesannonces.categorie;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class CategorieDtos {

    private CategorieDtos() {
    }

    /** Corps de POST /api/categories. Le libellé est nettoyé (strip) avant validation. */
    public record CategorieRequest(
            @NotBlank(message = "est obligatoire")
            @Size(min = 2, max = 50, message = "doit contenir entre 2 et 50 caractères")
            String libelle) {

        public CategorieRequest {
            libelle = libelle == null ? null : libelle.strip();
        }
    }

    public record CategorieResponse(Long id, String libelle) {

        static CategorieResponse depuis(Categorie categorie) {
            return new CategorieResponse(categorie.getId(), categorie.getLibelle());
        }
    }
}
