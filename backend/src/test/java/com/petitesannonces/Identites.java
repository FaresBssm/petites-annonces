package com.petitesannonces;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Simule les headers que la gateway pose après validation du token :
 * X_USER_ID = sub, X_ROLE_ID = rôles du realm (hiérarchie déjà résolue par Keycloak).
 */
public final class Identites {

    public static final String ALICE = "sub-alice";
    public static final String BOB = "sub-bob";
    public static final String MODO = "sub-modo";
    public static final String ADMIN = "sub-admin";

    private Identites() {
    }

    public static RequestPostProcessor alice() {
        return identite(ALICE, "USER");
    }

    public static RequestPostProcessor bob() {
        return identite(BOB, "USER");
    }

    public static RequestPostProcessor modo() {
        return identite(MODO, "MODERATEUR,USER");
    }

    public static RequestPostProcessor admin() {
        return identite(ADMIN, "ADMIN,MODERATEUR,USER");
    }

    public static RequestPostProcessor identite(String userId, String roles) {
        return requete -> {
            requete.addHeader("X_USER_ID", userId);
            requete.addHeader("X_ROLE_ID", roles);
            return requete;
        };
    }
}
