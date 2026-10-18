package com.petitesannonces;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// Pas d'utilisateur en mémoire ni de mot de passe généré : l'identité vient uniquement de la gateway
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class PetitesAnnoncesApplication {

    public static void main(String[] args) {
        SpringApplication.run(PetitesAnnoncesApplication.class, args);
    }
}
