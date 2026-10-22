package men.sbb8.web.starter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SsWebStarterTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Environment env;

    @Test
    void defaultsApplied() {
        assertEquals("30s", env.getProperty("spring.lifecycle.timeout-per-shutdown-phase"));
        assertEquals("true", env.getProperty("spring.threads.virtual.enabled"));
        assertEquals("non_null", env.getProperty("spring.jackson.default-property-inclusion"));
        assertEquals("3s", env.getProperty("spring.http.clients.connect-timeout"));
    }

    @Test
    void respCarriesTraceId() throws Exception {
        mvc.perform(get("/obj")).andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Autowired
    tools.jackson.databind.json.JsonMapper jsonMapper;

    @Test
    void jacksonUtilsUsesContainerMapper() {
        org.junit.jupiter.api.Assertions.assertSame(jsonMapper, men.sbb8.web.starter.json.JacksonUtils.mapper());
    }

    @Test
    void timeFormatConvention() throws Exception {
        // 请求体：LocalDateTime 兼容空格写法，Instant 为 ISO 带 Z
        mvc.perform(post("/time-body").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"at\":\"2026-01-02 03:04:05\",\"instant\":\"2026-01-02T03:04:05Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.at").value("2026-01-02T03:04:05"))
                .andExpect(jsonPath("$.data.instant").value("2026-01-02T03:04:05Z"));
        // 请求体：非法格式 -> 400
        mvc.perform(post("/time-body").contentType(MediaType.APPLICATION_JSON).content("{\"at\":\"2026/01/02\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAMS_ERROR"));
        // 查询参数：按 ISO 绑定
        mvc.perform(get("/time").param("at", "2026-01-02T03:04:05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("2026-01-02T03:04:05"));
    }

    @Test
    void respEnvelope() throws Exception {
        mvc.perform(get("/obj"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.n").value(1))
                .andExpect(jsonPath("$.data.time").value("2026-01-02T03:04:05"))
                .andExpect(jsonPath("$.success").doesNotExist())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void respWithString() throws Exception {
        mvc.perform(get("/str"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data").value("hello"));
    }

    @Test
    void respWithoutData() throws Exception {
        mvc.perform(get("/void")).andExpect(status().isOk());
    }

    @Test
    void openApiDocsNotWrapped() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").exists());
    }

    @Test
    void businessException() throws Exception {
        mvc.perform(get("/biz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PARAMS_ERROR"));
    }

    @Test
    void unknownExceptionDoesNotLeakMessage() throws Exception {
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("SYSTEM_ERROR"))
                .andExpect(jsonPath("$.msg").value("系统异常"));
    }

    @Test
    void missingAndMismatchedParams() throws Exception {
        mvc.perform(get("/param")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PARAMS_LOSS"));
        mvc.perform(get("/param").param("id", "abc")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAMS_TRANSFER_ERROR"));
    }

    @Test
    void beanValidation() throws Exception {
        mvc.perform(post("/valid").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAMS_ERROR"))
                .andExpect(jsonPath("$.msg").value("name 不能为空"));
        mvc.perform(post("/valid").contentType(MediaType.APPLICATION_JSON).content("{bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("请求体格式错误"));
    }

    @Test
    void notFoundKeepsStatus() throws Exception {
        mvc.perform(get("/nope")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void userInfoFromHeaders() throws Exception {
        mvc.perform(get("/me").header("X-User-Id", "42").header("X-Channel-Id", "7").header("X-User-Name", "bob"))
                .andExpect(jsonPath("$.data.userId").value("42"))
                .andExpect(jsonPath("$.data.channelId").value(7))
                .andExpect(jsonPath("$.data.username").value("bob"));
    }
}
