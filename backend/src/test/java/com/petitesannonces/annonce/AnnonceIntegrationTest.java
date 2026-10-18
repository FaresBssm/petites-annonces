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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static com.petitesannonces.Identites.ALICE;
import static com.petitesannonces.Identites.admin;
import static com.petitesannonces.Identites.alice;
import static com.petitesannonces.Identites.bob;
import static com.petitesannonces.Identites.modo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AnnonceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    /** Catégorie propre à chaque test : isole les listes (base partagée entre les tests). */
    private long categorieId;

    @BeforeEach
    void categorieDuTest() {
        categorieId = jdbc.queryForObject("INSERT INTO categorie (libelle) VALUES (?) RETURNING id", Long.class,
                "Test " + UUID.randomUUID().toString().substring(0, 8));
    }

    // ---------- MT-01 : création ----------

    @Test
    void mt01_creationEnAttenteAvecAuteurDuTokenMemeSiLeCorpsEnFournitUnAutre() throws Exception {
        String corps = """
                {"titre":"Vélo de course","description":"Taille 56","prix":350.50,"categorieId":%d,
                 "auteurId":"sub-bob","statut":"PUBLIEE"}""".formatted(categorieId);

        mockMvc.perform(post("/api/annonces").with(alice()).contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("http://localhost/api/annonces/")))
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.auteurId").value(ALICE))
                .andExpect(jsonPath("$.titre").value("Vélo de course"))
                .andExpect(jsonPath("$.prix").value(350.50));
    }

    @Test
    void locationUtiliseLUrlPubliqueTransmiseParLaGateway() throws Exception {
        mockMvc.perform(post("/api/annonces").with(alice())
                        .header("X-Forwarded-Proto", "http")
                        .header("X-Forwarded-Host", "localhost")
                        .header("X-Forwarded-Port", "8080")
                        .contentType(MediaType.APPLICATION_JSON).content(corpsValide("Location publique")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("http://localhost:8080/api/annonces/")));
    }

    // ---------- MT-10 : validation ----------

    @Test
    void mt10_titreDe3Caracteres400AvecLeChamp() throws Exception {
        String corps = """
                {"titre":"Vél","prix":10,"categorieId":%d}""".formatted(categorieId);
        mockMvc.perform(post("/api/annonces").with(alice()).contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.titre").value("doit contenir entre 5 et 100 caractères"));
    }

    @Test
    void mt10_prixNegatif400AvecLeChamp() throws Exception {
        String corps = """
                {"titre":"Vélo de course","prix":-1,"categorieId":%d}""".formatted(categorieId);
        mockMvc.perform(post("/api/annonces").with(alice()).contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.prix").value("doit être positif ou nul"));
    }

    @Test
    void categorieInexistante400() throws Exception {
        String corps = """
                {"titre":"Vélo de course","prix":10,"categorieId":999999}""";
        mockMvc.perform(post("/api/annonces").with(alice()).contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.categorieId").value("ne correspond à aucune catégorie"));
    }

    @Test
    void descriptionDePlusDe2000Caracteres400() throws Exception {
        String corps = """
                {"titre":"Vélo de course","description":"%s","prix":10,"categorieId":%d}"""
                .formatted("x".repeat(2001), categorieId);
        mockMvc.perform(post("/api/annonces").with(alice()).contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.description").exists());
    }

    // ---------- MT-02 : visibilité d'une annonce non publiée ----------

    @Test
    void mt02_bobNeVoitPasLAnnonceEnAttenteDAlice404() throws Exception {
        String id = creer(alice(), "Annonce en attente");

        mockMvc.perform(get("/api/annonces/{id}", id).with(bob()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void lAuteurEtLeModerateurVoientLAnnonceEnAttente() throws Exception {
        String id = creer(alice(), "Annonce en attente");

        mockMvc.perform(get("/api/annonces/{id}", id).with(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"));
        mockMvc.perform(get("/api/annonces/{id}", id).with(modo()))
                .andExpect(status().isOk());
    }

    @Test
    void toutUtilisateurVoitUneAnnoncePubliee() throws Exception {
        String id = creer(alice(), "Annonce publiée");
        publier(id);

        mockMvc.perform(get("/api/annonces/{id}", id).with(bob()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("PUBLIEE"));
    }

    @Test
    void annonceInexistante404() throws Exception {
        mockMvc.perform(get("/api/annonces/{id}", UUID.randomUUID()).with(alice()))
                .andExpect(status().isNotFound());
    }

    @Test
    void identifiantMalForme400() throws Exception {
        mockMvc.perform(get("/api/annonces/{id}", "pas-un-uuid").with(alice()))
                .andExpect(status().isBadRequest());
    }

    // ---------- MT-03 : seul l'auteur modifie ; auteur / MODERATEUR / ADMIN suppriment ----------

    @Test
    void mt03_bobNePeutNiModifierNiSupprimerLAnnonceDAlice403() throws Exception {
        String id = creer(alice(), "Annonce d'alice");

        mockMvc.perform(put("/api/annonces/{id}", id).with(bob())
                        .contentType(MediaType.APPLICATION_JSON).content(corpsValide("Piratée par bob")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/annonces/{id}", id).with(bob()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/annonces/{id}", id).with(alice()))
                .andExpect(jsonPath("$.titre").value("Annonce d'alice"));
    }

    @Test
    void leModerateurNePeutPasModifierLAnnonceDUnAutre403() throws Exception {
        String id = creer(alice(), "Annonce d'alice");
        mockMvc.perform(put("/api/annonces/{id}", id).with(modo())
                        .contentType(MediaType.APPLICATION_JSON).content(corpsValide("Modifiée par modo")))
                .andExpect(status().isForbidden());
    }

    @Test
    void lAuteurSupprimeSonAnnonce204() throws Exception {
        String id = creer(alice(), "À supprimer");
        mockMvc.perform(delete("/api/annonces/{id}", id).with(alice()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/annonces/{id}", id).with(alice()))
                .andExpect(status().isNotFound());
    }

    @Test
    void moderateurEtAdminSuppriment204() throws Exception {
        String id1 = creer(alice(), "Supprimée par modo");
        String id2 = creer(alice(), "Supprimée par admin");
        mockMvc.perform(delete("/api/annonces/{id}", id1).with(modo())).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/annonces/{id}", id2).with(admin())).andExpect(status().isNoContent());
    }

    @Test
    void modifierOuSupprimerUneAnnonceInexistante404() throws Exception {
        UUID inconnu = UUID.randomUUID();
        mockMvc.perform(put("/api/annonces/{id}", inconnu).with(alice())
                        .contentType(MediaType.APPLICATION_JSON).content(corpsValide("Fantôme")))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/annonces/{id}", inconnu).with(bob()))
                .andExpect(status().isNotFound());
    }

    // ---------- MT-04 : modifier une annonce publiée la renvoie en modération ----------

    @Test
    void mt04_modifierUneAnnoncePublieeLaRepasseEnAttenteEtLaRetireDeLaListePublique() throws Exception {
        String id = creer(alice(), "Canapé trois places");
        publier(id);
        mockMvc.perform(get("/api/annonces").with(bob()).param("categorieId", String.valueOf(categorieId)))
                .andExpect(jsonPath("$.contenu[*].id", hasItem(id)));

        mockMvc.perform(put("/api/annonces/{id}", id).with(alice())
                        .contentType(MediaType.APPLICATION_JSON).content(corpsValide("Canapé trois places, prix baissé")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.titre").value("Canapé trois places, prix baissé"));

        mockMvc.perform(get("/api/annonces").with(bob()).param("categorieId", String.valueOf(categorieId)))
                .andExpect(jsonPath("$.contenu[*].id", not(hasItem(id))));
    }

    // ---------- Liste publique, pagination, filtre ----------

    @Test
    void laListePubliqueNeContientQueDesAnnoncesPubliees() throws Exception {
        String publiee = creer(alice(), "Publiée");
        publier(publiee);
        String enAttente = creer(alice(), "En attente");

        mockMvc.perform(get("/api/annonces").with(bob()).param("categorieId", String.valueOf(categorieId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.contenu[0].id").value(publiee))
                .andExpect(jsonPath("$.contenu[*].id", not(hasItem(enAttente))))
                .andExpect(jsonPath("$.contenu[*].statut", everyItem(is("PUBLIEE"))));
    }

    @Test
    void paginationEtTriDuPlusRecentAuPlusAncien() throws Exception {
        for (int i = 1; i <= 3; i++) {
            publier(creer(alice(), "Annonce numéro " + i));
        }

        mockMvc.perform(get("/api/annonces").with(bob())
                        .param("categorieId", String.valueOf(categorieId)).param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenu.length()").value(2))
                .andExpect(jsonPath("$.contenu[0].titre").value("Annonce numéro 3"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void sizeSuperieureA50Refusee400() throws Exception {
        mockMvc.perform(get("/api/annonces").with(alice()).param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // ---------- Mes annonces ----------

    @Test
    void mesAnnoncesRenvoieToutesLesAnnoncesDeLAuteurEtSeulementLesSiennes() throws Exception {
        String publiee = creer(alice(), "Alice publiée");
        publier(publiee);
        String enAttente = creer(alice(), "Alice en attente");
        String deBob = creer(bob(), "Annonce de bob");

        MvcResult resultat = mockMvc.perform(get("/api/annonces/mes-annonces").with(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(publiee)))
                .andExpect(jsonPath("$[*].id", hasItem(enAttente)))
                .andExpect(jsonPath("$[*].id", not(hasItem(deBob))))
                .andReturn();
        JsonNode liste = objectMapper.readTree(resultat.getResponse().getContentAsString());
        liste.forEach(annonce -> assertThat(annonce.get("auteurId").asText()).isEqualTo(ALICE));
    }

    // ---------- outils ----------

    private String creer(RequestPostProcessor auteur, String titre) throws Exception {
        MvcResult resultat = mockMvc.perform(post("/api/annonces").with(auteur)
                        .contentType(MediaType.APPLICATION_JSON).content(corpsValide(titre)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(resultat.getResponse().getContentAsString()).get("id").asText();
    }

    /** Publication par le modérateur, via l'API de modération. */
    private void publier(String id) throws Exception {
        mockMvc.perform(patch("/api/moderation/annonces/{id}/publier", id).with(modo()))
                .andExpect(status().isOk());
    }

    private String corpsValide(String titre) {
        return """
                {"titre":"%s","description":"Description","prix":100,"categorieId":%d}"""
                .formatted(titre, categorieId);
    }
}
