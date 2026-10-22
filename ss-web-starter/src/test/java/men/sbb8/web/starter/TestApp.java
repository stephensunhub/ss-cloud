package men.sbb8.web.starter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import men.sbb8.web.starter.dto.UserInfo;
import men.sbb8.web.starter.dto.resp.RawResponse;
import men.sbb8.web.starter.error.BusinessException;
import men.sbb8.web.starter.error.CommonCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.service.registry.ImportHttpServices;

import java.time.LocalDateTime;
import java.util.Map;

@SpringBootApplication
@ImportHttpServices(group = "demo", types = DemoClient.class)
class TestApp {

    record TimeBody(LocalDateTime at, java.time.Instant instant) {
    }

    record Body(@NotBlank(message = "name 不能为空") String name) {
    }

    @RestController
    static class DemoController {

        @Autowired
        DemoClient demoClient;

        @GetMapping("/obj")
        Map<String, Object> obj() {
            return Map.of("time", LocalDateTime.of(2026, 1, 2, 3, 4, 5), "n", 1);
        }

        @GetMapping("/str")
        String str() {
            return "hello";
        }

        @GetMapping("/void")
        void none() {
        }

        @RawResponse
        @GetMapping("/raw")
        Map<String, Object> raw() {
            return Map.of("a", 1);
        }

        @GetMapping("/biz")
        String biz() {
            throw new BusinessException(CommonCode.PARAMS_ERROR);
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("secret internal detail");
        }

        @GetMapping("/param")
        String param(@RequestParam int id) {
            return "x";
        }

        @PostMapping("/valid")
        String valid(@Valid @RequestBody Body body) {
            return body.name();
        }

        @GetMapping("/time")
        LocalDateTime time(@RequestParam LocalDateTime at) {
            return at;
        }

        @PostMapping("/time-body")
        TimeBody timeBody(@RequestBody TimeBody body) {
            return body;
        }

        @GetMapping("/ip")
        String ip() {
            return men.sbb8.web.starter.utils.WebUtils.getRemoteIp();
        }

        @GetMapping("/uid")
        Long uid() {
            return men.sbb8.web.starter.utils.WebUtils.getCurrentUserId();
        }

        @GetMapping("/page")
        Map<String, Object> page(@Valid men.sbb8.web.starter.dto.req.PageReq req) {
            return Map.of("page", req.getPage(), "size", req.getSize());
        }

        @GetMapping("/me")
        UserInfo me(UserInfo userInfo) {
            return userInfo;
        }

        /** 经声明式客户端回调自己的 /me，验证用户头透传 */
        @GetMapping("/relay")
        UserInfo relay() {
            return demoClient.me().unwrap();
        }

        /** 下游 5xx 经全局异常处理后对外应是 502 */
        @GetMapping("/relay-boom")
        String relayBoom() {
            demoClient.boom();
            return "unreachable";
        }
    }
}
