package men.sbb8.web.starter;

import com.fasterxml.jackson.annotation.JsonFormat;
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

    @RestController
    static class ExtraController {
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
        ExtraController rawController() {
            return new ExtraController();
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
                .andExpect(status().is(418))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("APP-HANDLER"));
        // 应用没处理的异常，仍由 starter 兜底
        mvc.perform(get("/boom")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("SYSTEM_ERROR"));
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
    void openApiDescribesRespReturnTypes() throws Exception {
        String docs = mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
        var root = JacksonUtils.readTree(docs);
        // 控制器返回 Resp<T>，文档里就是带 code / msg / data / traceId 的结构
        var content = root.at("/paths/~1obj/get/responses/200/content");
        String ref = content.properties().iterator().next().getValue().path("schema").path("$ref").asString();
        var schema = root.at("/components/schemas/" + ref.substring(ref.lastIndexOf('/') + 1));
        var properties = new java.util.TreeSet<String>();
        schema.path("properties").propertyNames().forEach(properties::add);
        assertEquals(java.util.Set.of("code", "msg", "data", "traceId"), properties);
        // UserInfo 来自请求头，不是请求参数
        assertEquals(0, root.at("/paths/~1me/get").path("parameters").size());
    }
}
