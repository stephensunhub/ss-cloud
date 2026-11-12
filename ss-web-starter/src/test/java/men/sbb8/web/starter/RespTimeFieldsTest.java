package men.sbb8.web.starter;

import men.sbb8.web.starter.dto.resp.Resp;
import men.sbb8.web.starter.error.BusinessException;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 信封带 requestTime（请求到达服务的时间）和 responseTime（生成响应的时间），都是毫秒时间戳的字符串。
 * 成功和失败的响应都有；不在请求里构造的 Resp，requestTime 等于 responseTime
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(RespTimeFieldsTest.Extra.class)
class RespTimeFieldsTest {

    @RestController
    static class TimedController {
        @GetMapping("/timed-ok")
        Resp<String> ok() throws InterruptedException {
            Thread.sleep(30);
            return Resp.ok("x");
        }

        @GetMapping("/timed-fail")
        Resp<String> fail() {
            throw new BusinessException("SOME_CODE", "some message");
        }
    }

    @TestConfiguration
    static class Extra {
        @Bean
        TimedController timedController() {
            return new TimedController();
        }
    }

    @Autowired
    MockMvc mvc;

    private JsonNode call(String path) throws Exception {
        return JsonMapper.builder().build().readTree(mvc.perform(get(path)).andReturn().getResponse().getContentAsString());
    }

    @Test
    void aSuccessfulResponseCarriesBothTimes() throws Exception {
        long before = System.currentTimeMillis();
        JsonNode body = call("/timed-ok");
        long after = System.currentTimeMillis();
        assertTrue(body.get("requestTime").isString() && body.get("responseTime").isString(), "both are strings: " + body);
        long requestTime = Long.parseLong(body.get("requestTime").asString());
        long responseTime = Long.parseLong(body.get("responseTime").asString());
        assertTrue(requestTime >= before && responseTime <= after, body.toString());
        assertTrue(responseTime - requestTime >= 30, "the request time is when the request arrived, not when the body was built: " + body);
    }

    @Test
    void anErrorResponseCarriesBothTimes() throws Exception {
        JsonNode body = call("/timed-fail");
        assertEquals("SOME_CODE", body.get("code").asString());
        assertNotNull(body.get("requestTime"), body.toString());
        assertTrue(Long.parseLong(body.get("responseTime").asString()) >= Long.parseLong(body.get("requestTime").asString()));
    }

    @Test
    void outsideARequestTheRequestTimeFallsBackToTheResponseTime() {
        Resp<String> resp = Resp.ok("x");
        assertEquals(resp.getResponseTime(), resp.getRequestTime());
    }
}
