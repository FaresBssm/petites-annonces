package com.petitesannonces.erreur;

/** Traduite en 409 ProblemDetail par {@link GestionnaireErreurs}. */
public class ConflitException extends RuntimeException {

    public ConflitException(String message) {
        super(message);
    }
}
