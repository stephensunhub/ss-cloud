package men.sbb8.web.starter;

import men.sbb8.web.starter.dto.resp.Resp;
import men.sbb8.web.starter.error.BusinessException;
import men.sbb8.web.starter.error.CommonCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 配置了 ss.web.wrap-response.base-packages 后，成功返回值被包装；String、文件、Resp、错误响应原样
 */
@SpringBootTest(properties = "ss.web.wrap-response.base-packages=men.sbb8.web.starter")
@AutoConfigureMockMvc
@Import(ResponseWrapTest.Extra.class)
class ResponseWrapTest {

    @Autowired
    MockMvc mvc;

    @TestConfiguration
    @RestController
    static class Extra {

        @GetMapping("/wrap/obj")
        Map<String, Object> obj() {
            return Map.of("n", 1);
        }

        @GetMapping("/wrap/str")
        String str() {
            return "plain";
        }

        @GetMapping("/wrap/resp")
        Resp<String> resp() {
            return Resp.ok("already");
        }

        @GetMapping("/wrap/file")
        Resource file() {
            return new ByteArrayResource("hello".getBytes()) {
                @Override
                public String getFilename() {
                    return "报告.txt";
                }
            };
        }

        @GetMapping("/wrap/bytes")
        byte[] bytes() {
            return "raw".getBytes();
        }

        @GetMapping("/wrap/fail")
        Map<String, Object> fail() {
            throw new BusinessException(CommonCode.UNAUTHORIZED);
        }
    }

    @Test
    void objectIsWrapped() throws Exception {
        mvc.perform(get("/wrap/obj"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.n").value(1));
    }

    @Test
    void stringIsReturnedAsIs() throws Exception {
        mvc.perform(get("/wrap/str")).andExpect(status().isOk()).andExpect(content().string("plain"));
    }

    @Test
    void respIsNotWrappedTwice() throws Exception {
        mvc.perform(get("/wrap/resp"))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data").value("already"));
    }

    @Test
    void fileIsReturnedAsIsWithAttachmentName() throws Exception {
        var result = mvc.perform(get("/wrap/file"))
                .andExpect(status().isOk())
                .andExpect(content().string("hello"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andReturn();
        String disposition = result.getResponse().getHeader("Content-Disposition");
        // 文件名按 RFC 6266 的 filename* 用 UTF-8 编码，不是 form-data
        assertEquals(true, disposition != null && disposition.contains("filename*=UTF-8''") && !disposition.contains("form-data"), disposition);
    }

    @Test
    void bytesAreReturnedAsIs() throws Exception {
        mvc.perform(get("/wrap/bytes")).andExpect(status().isOk()).andExpect(content().bytes("raw".getBytes()));
    }

    @Test
    void errorResponseIsStillAnError() throws Exception {
        mvc.perform(get("/wrap/fail"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}

