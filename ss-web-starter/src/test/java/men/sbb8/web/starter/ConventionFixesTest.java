package men.sbb8.web.starter;

import com.fasterxml.jackson.annotation.JsonFormat;
import men.sbb8.web.starter.dto.resp.RawResponse;
import men.sbb8.web.starter.error.BusinessException;
import men.sbb8.web.starter.json.JacksonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ConventionFixesTest.Extra.class)
class ConventionFixesTest {

    @RawResponse
    @RestController
    static class RawController {
        @GetMapping("/raw-class")
        Map<String, Object> raw() {
            return Map.of("a", 1);
        }

        @GetMapping("/wrapped-cause")
        String wrappedCause() {
            throw new BusinessException(new IllegalStateException("secret"));
        }

        @GetMapping("/unsupported")
        String unsupported() {
            throw new UnsupportedOperationException();
        }
    }

    @TestConfiguration
    static class Extra {
        @Bean
        RawController rawController() {
            return new RawController();
        }
    }

    record Stamped(@JsonFormat(pattern = "dd/MM/yyyy HH:mm") LocalDateTime at) {
    }

    record Plain(LocalDateTime at) {
    }

    @Autowired
    MockMvc mvc;

    @Test
    void applicationAdviceOverridesStarterHandler() throws Exception {
        mvc.perform(get("/unsupported"))
                .andExpect(status().isIAmATeapot())
                .andExpect(jsonPath("$.data").value("APP-HANDLER"));
        // 应用没处理的异常，仍由 starter 兜底
        mvc.perform(get("/boom")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("SYSTEM_ERROR"));
    }

    @Test
    void onlyJsonResponsesAreWrapped() throws Exception {
        mvc.perform(get("/plain")).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("plain"));
        mvc.perform(get("/json-str")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("text"));
        mvc.perform(get("/str")).andExpect(jsonPath("$.data").value("hello"));
    }

    @Test
    void rawResponseOnClass() throws Exception {
        mvc.perform(get("/raw-class")).andExpect(status().isOk()).andExpect(jsonPath("$.a").value(1));
    }

    @Test
    void businessExceptionWrappingCause() throws Exception {
        mvc.perform(get("/wrapped-cause")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("SYSTEM_ERROR"))
                .andExpect(jsonPath("$.msg").value("系统异常"));
    }

    @Test
    void jsonFormatOnFieldStillWorks() {
        assertEquals(LocalDateTime.of(2026, 1, 2, 3, 4),
                JacksonUtils.fromJson("{\"at\":\"02/01/2026 03:04\"}", Stamped.class).at());
        assertThrows(JacksonException.class, () -> JacksonUtils.fromJson("{\"at\":\"2026-01-02T03:04:05\"}", Stamped.class));
    }

    @Test
    void spaceSeparatedStillAcceptedWithoutPattern() {
        assertEquals(LocalDateTime.of(2026, 1, 2, 3, 4, 5),
                JacksonUtils.fromJson("{\"at\":\"2026-01-02 03:04:05\"}", Plain.class).at());
        assertEquals(LocalDateTime.of(2026, 1, 2, 3, 4, 5),
                JacksonUtils.fromJson("{\"at\":\"2026-01-02T03:04:05\"}", Plain.class).at());
        assertThrows(JacksonException.class, () -> JacksonUtils.fromJson("{\"at\":\"2026/01/02\"}", Plain.class));
    }

    @Test
    void openApiDescribesWrappedResponses() throws Exception {
        String docs = mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
        var root = JacksonUtils.readTree(docs);
        var obj = root.at("/paths/~1obj/get/responses/200/content/application~1json/schema");
        assertEquals("code,msg,traceId,data", String.join(",", obj.path("properties").propertyNames()));
        // void 接口也是 Resp，只是没有 data
        var none = root.at("/paths/~1void/get/responses/200/content/application~1json/schema");
        assertEquals("code,msg,traceId", String.join(",", none.path("properties").propertyNames()));
        // @RawResponse 的接口保持原样
        var raw = root.at("/paths/~1raw/get/responses/200/content/*~1*/schema");
        assertEquals("object", raw.path("type").asString());
        // UserInfo 来自请求头，不是请求参数
        assertEquals(0, root.at("/paths/~1me/get").path("parameters").size());
    }
}
