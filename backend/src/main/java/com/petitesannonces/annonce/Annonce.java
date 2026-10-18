package com.petitesannonces.annonce;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "annonce")
public class Annonce {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String titre;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal prix;

    @Column(name = "categorie_id", nullable = false)
    private Long categorieId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Statut statut;

    /** Claim sub du token (X_USER_ID), jamais fourni par le client. */
    @Column(name = "auteur_id", nullable = false, length = 64, updatable = false)
    private String auteurId;

    @Column(name = "motif_rejet", length = 500)
    private String motifRejet;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private Instant dateCreation;

    @Column(name = "date_modification", nullable = false)
    private Instant dateModification;

    protected Annonce() {
        // pour JPA
    }

    /** Toute nouvelle annonce part en modération. */
    public Annonce(String titre, String description, BigDecimal prix, Long categorieId, String auteurId) {
        this.id = UUID.randomUUID();
        this.titre = titre;
        this.description = description;
        this.prix = prix;
        this.categorieId = categorieId;
        this.auteurId = auteurId;
        this.statut = Statut.EN_ATTENTE;
        this.dateCreation = Instant.now();
        this.dateModification = this.dateCreation;
    }

    /** Modification par l'auteur : l'annonce repasse en modération (MT-04). */
    public void modifier(String titre, String description, BigDecimal prix, Long categorieId) {
        this.titre = titre;
        this.description = description;
        this.prix = prix;
        this.categorieId = categorieId;
        this.statut = Statut.EN_ATTENTE;
        this.motifRejet = null;
        this.dateModification = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTitre() {
        return titre;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrix() {
        return prix;
    }

    public Long getCategorieId() {
        return categorieId;
    }

    public Statut getStatut() {
        return statut;
    }

    public String getAuteurId() {
        return auteurId;
    }

    public String getMotifRejet() {
        return motifRejet;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }

    public Instant getDateModification() {
        return dateModification;
    }
}
