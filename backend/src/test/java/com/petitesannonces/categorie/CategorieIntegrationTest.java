package com.petitesannonces.categorie;

import com.petitesannonces.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.petitesannonces.Identites.admin;
import static com.petitesannonces.Identites.alice;
import static com.petitesannonces.Identites.modo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CategorieIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    // ---------- Lecture ----------

    @Test
    void userListeLesCategoriesTrieesParLibelle() throws Exception {
        mockMvc.perform(get("/api/categories").with(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].libelle", hasItems("Emploi", "Immobilier", "Multimédia", "Véhicules")));
    }

    @Test
    void sansIdentite401() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- MT-08 : création réservée à ADMIN ----------

    @Test
    void mt08_moderateurNePeutPasCreer403() throws Exception {
        mockMvc.perform(post("/api/categories").with(modo()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"libelle\":\"Animaux\"}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void mt08_adminCree201() throws Exception {
        String libelle = unique("Loisirs");
        mockMvc.perform(post("/api/categories").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"libelle\":\"  " + libelle + "  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.libelle").value(libelle));

        mockMvc.perform(get("/api/categories").with(alice()))
                .andExpect(jsonPath("$[*].libelle", hasItem(libelle)));
    }

    @Test
    void userNePeutPasCreer403() throws Exception {
        mockMvc.perform(post("/api/categories").with(alice()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"libelle\":\"Animaux\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void libelleDejaExistantQuelleQueSoitLaCasse409() throws Exception {
        mockMvc.perform(post("/api/categories").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"libelle\":\"emploi\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void libelleTropCourt400AvecDetailDuChamp() throws Exception {
        mockMvc.perform(post("/api/categories").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"libelle\":\" A \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.libelle").value("doit contenir entre 2 et 50 caractères"));
    }

    @Test
    void libelleAbsent400() throws Exception {
        mockMvc.perform(post("/api/categories").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.libelle").value("est obligatoire"));
    }

    // ---------- Suppression ----------

    @Test
    void adminSupprimeUneCategorieInutilisee204() throws Exception {
        long id = creerCategorie(unique("Jardin"));

        mockMvc.perform(delete("/api/categories/{id}", id).with(admin()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/categories").with(alice()))
                .andExpect(jsonPath("$[*].id", not(hasItem((int) id))));
    }

    @Test
    void moderateurNePeutPasSupprimer403() throws Exception {
        long id = creerCategorie(unique("Sport"));
        mockMvc.perform(delete("/api/categories/{id}", id).with(modo()))
                .andExpect(status().isForbidden());
    }

    @Test
    void supprimerCategorieInexistante404() throws Exception {
        mockMvc.perform(delete("/api/categories/{id}", 999_999).with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void mt09_categorieUtiliseeParUneAnnonce409() throws Exception {
        long id = creerCategorie(unique("Mode"));
        jdbc.update("""
                INSERT INTO annonce (id, titre, description, prix, categorie_id, statut, auteur_id,
                                     date_creation, date_modification)
                VALUES (?, 'Veste en cuir', 'Taille M', 50, ?, 'EN_ATTENTE', 'sub-alice', now(), now())
                """, UUID.randomUUID(), id);

        mockMvc.perform(delete("/api/categories/{id}", id).with(admin()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409));

        mockMvc.perform(get("/api/categories").with(alice()))
                .andExpect(jsonPath("$[*].id", hasItem((int) id)));
    }

    // ---------- outils ----------

    private long creerCategorie(String libelle) {
        return jdbc.queryForObject("INSERT INTO categorie (libelle) VALUES (?) RETURNING id", Long.class, libelle);
    }

    /** La base est partagée entre les classes de test : des libellés uniques évitent les collisions. */
    private static String unique(String prefixe) {
        return prefixe + " " + UUID.randomUUID().toString().substring(0, 8);
    }
}
