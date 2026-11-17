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
// 激活应用自己的生产 profile（测试资源里的 application-online.yml），它覆盖 starter 的 application.yaml
@ActiveProfiles("online")
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
    void scrapeTargetMatchesGrpcServices() {
        org.junit.jupiter.api.Assertions.assertEquals("9091", env.getProperty("management.server.port"));
        org.junit.jupiter.api.Assertions.assertEquals("/", env.getProperty("management.endpoints.web.base-path"));
        org.junit.jupiter.api.Assertions.assertEquals("metrics", env.getProperty("management.endpoints.web.path-mapping.prometheus"));
    }

    @Test
    void userInfoRequiredWhenConfigured() throws Exception {
        mvc.perform(get("/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/me").header("X-User-Id", "7")).andExpect(status().isOk());
    }
}
