package com.petitesannonces.annonce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petitesannonces.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.petitesannonces.Identites.admin;
import static com.petitesannonces.Identites.alice;
import static com.petitesannonces.Identites.bob;
import static com.petitesannonces.Identites.modo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ModerationIntegrationTest {

    private static final String MODERATION = "/api/moderation/annonces";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private long categorieId;

    @BeforeEach
    void categorieDuTest() {
        categorieId = jdbc.queryForObject("INSERT INTO categorie (libelle) VALUES (?) RETURNING id", Long.class,
                "Modération " + UUID.randomUUID().toString().substring(0, 8));
    }

    // ---------- MT-05 : un USER ne modère pas ----------

    @Test
    void mt05_aliceNePeutPasPublier403() throws Exception {
        String id = creerParAlice("Annonce à publier");

        mockMvc.perform(patch(MODERATION + "/{id}/publier", id).with(alice()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(get("/api/annonces/{id}", id).with(alice()))
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"));
    }

    @Test
    void unUserNeVoitPasLaFileDeModerationNiNeRejette403() throws Exception {
        String id = creerParAlice("Annonce à rejeter");
        mockMvc.perform(get(MODERATION).with(bob()))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch(MODERATION + "/{id}/rejeter", id).with(bob())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"Non\"}"))
                .andExpect(status().isForbidden());
    }

    // ---------- MT-06 : publication ----------

    @Test
    void mt06_modoPublieEtLAnnonceApparaitDansLaListePublique() throws Exception {
        String id = creerParAlice("Table en chêne");

        mockMvc.perform(patch(MODERATION + "/{id}/publier", id).with(modo()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("PUBLIEE"));

        mockMvc.perform(get("/api/annonces").with(bob()).param("categorieId", String.valueOf(categorieId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenu[*].id", hasItem(id)));
    }

    @Test
    void ladminModereAussiGraceAuRoleComposite() throws Exception {
        String id = creerParAlice("Publiée par admin");
        mockMvc.perform(patch(MODERATION + "/{id}/publier", id).with(admin()))
                .andExpect(status().isOk());
    }

    // ---------- MT-07 : rejet ----------

    @Test
    void mt07_rejetSansMotif400ProblemDetail() throws Exception {
        String id = creerParAlice("Annonce douteuse");

        mockMvc.perform(patch(MODERATION + "/{id}/rejeter", id).with(modo())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.motif").value("est obligatoire"));
    }

    @Test
    void mt07_rejetAvecMotifVideOuSansCorps400() throws Exception {
        String id = creerParAlice("Annonce douteuse");

        mockMvc.perform(patch(MODERATION + "/{id}/rejeter", id).with(modo())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.motif").value("est obligatoire"));
        mockMvc.perform(patch(MODERATION + "/{id}/rejeter", id).with(modo()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(get("/api/annonces/{id}", id).with(alice()))
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"));
    }

    @Test
    void rejetAvecMotifVisibleParLAuteurMaisPasPublic() throws Exception {
        String id = creerParAlice("Annonce hors charte");

        mockMvc.perform(patch(MODERATION + "/{id}/rejeter", id).with(modo())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"Photos manquantes\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("REJETEE"))
                .andExpect(jsonPath("$.motifRejet").value("Photos manquantes"));

        mockMvc.perform(get("/api/annonces/mes-annonces").with(alice()))
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].motifRejet", hasItem("Photos manquantes")));
        mockMvc.perform(get("/api/annonces/{id}", id).with(bob()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/annonces").with(bob()).param("categorieId", String.valueOf(categorieId)))
                .andExpect(jsonPath("$.contenu[*].id", not(hasItem(id))));
    }

    @Test
    void uneAnnonceRejeteePuisModifieeRevientEnAttenteSansMotif() throws Exception {
        String id = creerParAlice("Annonce à corriger");
        mockMvc.perform(patch(MODERATION + "/{id}/rejeter", id).with(modo())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"Prix incohérent\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/annonces/{id}", id).with(alice())
                        .contentType(MediaType.APPLICATION_JSON).content(corps("Annonce corrigée")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.motifRejet").doesNotExist());
    }

    // ---------- Transitions et file d'attente ----------

    @Test
    void seuleUneAnnonceEnAttentePeutEtreModeree409() throws Exception {
        String id = creerParAlice("Déjà publiée");
        mockMvc.perform(patch(MODERATION + "/{id}/publier", id).with(modo())).andExpect(status().isOk());

        mockMvc.perform(patch(MODERATION + "/{id}/publier", id).with(modo()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mockMvc.perform(patch(MODERATION + "/{id}/rejeter", id).with(modo())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"Trop tard\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void modererUneAnnonceInexistante404() throws Exception {
        mockMvc.perform(patch(MODERATION + "/{id}/publier", UUID.randomUUID()).with(modo()))
                .andExpect(status().isNotFound());
    }

    @Test
    void laFileNeContientQueDesAnnoncesEnAttentePlusAnciennesDAbord() throws Exception {
        String premiere = creerParAlice("Première arrivée");
        String seconde = creerParAlice("Seconde arrivée");
        String publiee = creerParAlice("Sortie de la file");
        mockMvc.perform(patch(MODERATION + "/{id}/publier", publiee).with(modo())).andExpect(status().isOk());

        MvcResult resultat = mockMvc.perform(get(MODERATION).with(modo()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].statut", everyItem(is("EN_ATTENTE"))))
                .andExpect(jsonPath("$[*].id", not(hasItem(publiee))))
                .andReturn();

        List<String> ids = new ArrayList<>();
        for (JsonNode annonce : objectMapper.readTree(resultat.getResponse().getContentAsString())) {
            ids.add(annonce.get("id").asText());
        }
        assertThat(ids).containsSubsequence(premiere, seconde);
    }

    // ---------- outils ----------

    private String creerParAlice(String titre) throws Exception {
        MvcResult resultat = mockMvc.perform(post("/api/annonces").with(alice())
                        .contentType(MediaType.APPLICATION_JSON).content(corps(titre)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(resultat.getResponse().getContentAsString()).get("id").asText();
    }

    private String corps(String titre) {
        return """
                {"titre":"%s","description":"Description","prix":42,"categorieId":%d}"""
                .formatted(titre, categorieId);
    }
}
