package com.petitesannonces.security;

import com.petitesannonces.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static com.petitesannonces.Identites.admin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Bonus : avec un secret configuré, seuls les appels portant X-Gateway-Secret sont acceptés. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "petitesannonces.gateway-secret=secret-de-test")
class GatewaySecretIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void headersDIdentiteSansSecretRefuses403() throws Exception {
        // Ce que ferait un autre conteneur du réseau internal : headers d'identité forgés, sans secret
        mockMvc.perform(get("/api/me").with(admin()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Appel refusé : la requête ne provient pas de la gateway."));
    }

    @Test
    void mauvaisSecretRefuse403() throws Exception {
        mockMvc.perform(get("/api/me").with(admin()).header(GatewaySecretFilter.HEADER, "secret-de-tesT"))
                .andExpect(status().isForbidden());
    }

    @Test
    void bonSecretAccepte() throws Exception {
        mockMvc.perform(get("/api/me").with(admin()).header(GatewaySecretFilter.HEADER, "secret-de-test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("sub-admin"));
    }

    @Test
    void healthcheckNonConcerne() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
