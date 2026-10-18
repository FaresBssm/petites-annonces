package com.petitesannonces.security;

import com.petitesannonces.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sécurité reconstruite depuis les headers de la gateway : les headers sont injectés
 * directement, comme si Kong les avait posés.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SecuriteHeadersIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // ---------- BK-02 : pas d'identité -> 401 ProblemDetail ----------

    @Test
    void bk02_sansXUserId_401AuFormatProblemDetail() throws Exception {
        mockMvc.perform(get("/api/me").header("X_ROLE_ID", "USER"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.instance").value("/api/me"));
    }

    @Test
    void bk02_sansAucunHeader_401() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bk02_headersVides_401() throws Exception {
        mockMvc.perform(get("/api/me").header("X_USER_ID", "   ").header("X_ROLE_ID", "USER"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me").header("X_USER_ID", "u1").header("X_ROLE_ID", ""))
                .andExpect(status().isUnauthorized());
    }

    // ---------- BK-03 : contexte reconstruit ----------

    @Test
    void bk03_securityContextHolderContientPrincipalEtAutorites() throws Exception {
        mockMvc.perform(get("/api/test/contexte")
                        .header("X_USER_ID", "u1")
                        .header("X_ROLE_ID", "MODERATEUR,USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principal").value("u1"))
                .andExpect(jsonPath("$.autorites", containsInAnyOrder("ROLE_MODERATEUR", "ROLE_USER")))
                .andExpect(jsonPath("$.type").value("PreAuthenticatedAuthenticationToken"))
                .andExpect(jsonPath("$.authentifie").value(true));
    }

    @Test
    void me_renvoieUserIdEtRolesDuContexte() throws Exception {
        mockMvc.perform(get("/api/me").header("X_USER_ID", "u1").header("X_ROLE_ID", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("u1"))
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value("USER"));
    }

    @Test
    void rolesInconnusOuMalFormesIgnores() throws Exception {
        mockMvc.perform(get("/api/me").header("X_USER_ID", "u1").header("X_ROLE_ID", " USER , SUPERADMIN,admin,"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value("USER"));
    }

    // ---------- BK-05 : aucune fuite entre requêtes, aucune session ----------

    @Test
    void bk05_contexteVideEntreDeuxRequetesEtAucuneSession() throws Exception {
        MvcResult alice = mockMvc.perform(get("/api/me").header("X_USER_ID", "alice-sub").header("X_ROLE_ID", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("alice-sub"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn();
        assertThat(alice.getRequest().getSession(false)).as("aucune session HTTP créée").isNull();
        // MockMvc exécute la requête dans le thread du test : le contexte doit avoir été vidé
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        mockMvc.perform(get("/api/me").header("X_USER_ID", "bob-sub").header("X_ROLE_ID", "MODERATEUR,USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("bob-sub"))
                .andExpect(jsonPath("$.roles", containsInAnyOrder("MODERATEUR", "USER")));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        // Une 3e requête sans headers n'hérite de personne
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- Périmètre ----------

    @Test
    void healthResteAccessibleSansIdentite() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void cheminHorsApiRefuseMemeAuthentifie() throws Exception {
        mockMvc.perform(get("/actuator/env").header("X_USER_ID", "u1").header("X_ROLE_ID", "ADMIN"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
