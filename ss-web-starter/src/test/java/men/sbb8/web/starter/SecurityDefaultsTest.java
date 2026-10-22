package men.sbb8.web.starter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityDefaultsTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Environment env;

    @Test
    void remoteIpIgnoresForgeableForwardedHeaders() throws Exception {
        mvc.perform(get("/ip").header("X-Forwarded-For", "1.2.3.4").header("X-Real-IP", "5.6.7.8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("127.0.0.1"));
        assertEquals("native", env.getProperty("server.forward-headers-strategy"));
    }

    @Test
    void currentUserIdMissingOrInvalidIs401() throws Exception {
        mvc.perform(get("/uid")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/uid").header("X-User-Id", "abc")).andExpect(status().isUnauthorized());
        mvc.perform(get("/uid").header("X-User-Id", "42")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(42));
    }

    @Test
    void userInfoNotRequiredByDefault() throws Exception {
        mvc.perform(get("/me")).andExpect(status().isOk());
    }

    @Test
    void pageSizeIsBounded() throws Exception {
        mvc.perform(get("/page")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1)).andExpect(jsonPath("$.data.size").value(20));
        mvc.perform(get("/page").param("size", "1000000")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAMS_ERROR"))
                .andExpect(jsonPath("$.msg").value("size 不能超过 200"));
        mvc.perform(get("/page").param("page", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/page").param("size", "0")).andExpect(status().isBadRequest());
    }

    @Test
    void apiDocsAvailableOutsideProd() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }
}
