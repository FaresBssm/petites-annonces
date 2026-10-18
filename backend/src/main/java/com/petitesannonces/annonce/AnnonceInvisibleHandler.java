package com.petitesannonces.annonce;

import com.petitesannonces.erreur.RessourceIntrouvableException;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.method.MethodAuthorizationDeniedHandler;
import org.springframework.security.authorization.method.MethodInvocationResult;
import org.springframework.stereotype.Component;

/**
 * Pour GET /api/annonces/{id} : une annonce que l'appelant n'a pas le droit de voir
 * doit paraître inexistante (404, MT-02) plutôt qu'interdite (403), pour ne pas
 * révéler son existence.
 */
@Component
public class AnnonceInvisibleHandler implements MethodAuthorizationDeniedHandler {

    /** Refus AVANT l'appel (@PreAuthorize, ex. pas le rôle USER) : 403 classique. */
    @Override
    public Object handleDeniedInvocation(MethodInvocation invocation, AuthorizationResult result) {
        throw new AuthorizationDeniedException("Access Denied", result);
    }

    /** Refus APRÈS l'appel (@PostAuthorize sur l'annonce chargée) : 404. */
    @Override
    public Object handleDeniedInvocationResult(MethodInvocationResult invocationResult, AuthorizationResult result) {
        throw new RessourceIntrouvableException("Annonce introuvable.");
    }
}
