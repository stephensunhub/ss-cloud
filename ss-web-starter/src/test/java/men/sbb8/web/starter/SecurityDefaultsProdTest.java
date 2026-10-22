package men.sbb8.web.starter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "ss.web.user-info.required=true")
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class SecurityDefaultsProdTest {

    @Autowired
    MockMvc mvc;

    @Test
    void apiDocsAndSwaggerUiOffInProd() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
    }

    @Autowired
    org.springframework.core.env.Environment env;

    @Test
    void tracingSampledAtTenPercentInProd() {
        org.junit.jupiter.api.Assertions.assertEquals("0.1", env.getProperty("management.tracing.sampling.probability"));
    }

    @Test
    void userInfoRequiredWhenConfigured() throws Exception {
        mvc.perform(get("/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/me").header("X-User-Id", "7")).andExpect(status().isOk());
    }
}
