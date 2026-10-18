package com.petitesannonces.erreur;

/** Traduite en 404 ProblemDetail par {@link GestionnaireErreurs}. */
public class RessourceIntrouvableException extends RuntimeException {

    public RessourceIntrouvableException(String message) {
        super(message);
    }
}
