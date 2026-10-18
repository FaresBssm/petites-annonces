package com.petitesannonces.annonce;

/** Cycle de vie : EN_ATTENTE -> PUBLIEE ou REJETEE ; toute modification par l'auteur -> EN_ATTENTE. */
public enum Statut {
    EN_ATTENTE,
    PUBLIEE,
    REJETEE
}
