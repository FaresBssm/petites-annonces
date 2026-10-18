package com.petitesannonces.erreur;

/**
 * Champ syntaxiquement valide mais refusé par une règle métier (ex. catégorie inexistante).
 * Traduite en 400 ProblemDetail, au même format que les erreurs de Bean Validation.
 */
public class ChampInvalideException extends RuntimeException {

    private final String champ;

    public ChampInvalideException(String champ, String message) {
        super(message);
        this.champ = champ;
    }

    public String getChamp() {
        return champ;
    }
}
