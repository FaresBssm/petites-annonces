package com.petitesannonces.trace;

import com.petitesannonces.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static com.petitesannonces.Identites.alice;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class CorrelationIdIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void lIdentifiantDeKongFigureDansLesLogsDuBackend(CapturedOutput sortie) throws Exception {
        mockMvc.perform(get("/api/me").with(alice()).header("X-Correlation-ID", "3f1c2a7e-0b1d-4c2e-9a77-1#42"))
                .andExpect(status().isOk());

        assertThat(sortie.getOut()).contains("[cid=3f1c2a7e-0b1d-4c2e-9a77-1#42]");
    }

    @Test
    void memeUneRequeteRefuseeEstTracee(CapturedOutput sortie) throws Exception {
        mockMvc.perform(get("/api/me").header("X-Correlation-ID", "refus-401"))
                .andExpect(status().isUnauthorized());

        assertThat(sortie.getOut()).contains("[cid=refus-401]");
    }

    @Test
    void uneValeurQuiPourraitFalsifierLesLogsEstIgnoree(CapturedOutput sortie) throws Exception {
        mockMvc.perform(get("/api/me").with(alice()).header("X-Correlation-ID", "x] INFO faux message"))
                .andExpect(status().isOk());

        assertThat(sortie.getOut()).doesNotContain("faux message").contains("[cid=]");
    }
}
