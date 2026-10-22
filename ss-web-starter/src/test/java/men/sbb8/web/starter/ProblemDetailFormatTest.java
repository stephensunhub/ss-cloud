package men.sbb8.web.starter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "ss.web.error-format=problem-detail")
@AutoConfigureMockMvc
class ProblemDetailFormatTest {

    @Autowired
    MockMvc mvc;

    @Test
    void businessErrorIsProblemJsonWith422InsteadOf200() throws Exception {
        mvc.perform(get("/biz"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.code").value("PARAMS_ERROR"))
                .andExpect(jsonPath("$.detail").value("参数错误"))
                .andExpect(jsonPath("$.title").value("Unprocessable Content"));
    }

    @Test
    void unknownErrorDoesNotLeakDetails() throws Exception {
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("SYSTEM_ERROR"))
                .andExpect(jsonPath("$.detail").value("系统异常"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }

    @Test
    void validationAndFrameworkErrors() throws Exception {
        mvc.perform(post("/valid").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAMS_ERROR"))
                .andExpect(jsonPath("$.detail").value("name 不能为空"));
        mvc.perform(get("/no-such-path"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void successResponsesStillUseTheResponseEnvelope() throws Exception {
        mvc.perform(get("/obj")).andExpect(status().isOk()).andExpect(jsonPath("$.data.n").value(1));
    }
}
