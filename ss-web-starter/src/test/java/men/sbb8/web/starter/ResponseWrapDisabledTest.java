package men.sbb8.web.starter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 默认（没配 base-packages）不包装；配了但包名不匹配的接口也不包装
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ResponseWrapDisabledTest.Extra.class)
class ResponseWrapDisabledTest {

    @Autowired
    MockMvc mvc;

    @TestConfiguration
    @RestController
    static class Extra {
        @GetMapping("/nowrap/obj")
        Map<String, Object> obj() {
            return Map.of("n", 1);
        }
    }

    @Test
    void notWrappedByDefault() throws Exception {
        mvc.perform(get("/nowrap/obj"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.n").value(1))
                .andExpect(jsonPath("$.code").doesNotExist());
    }
}
