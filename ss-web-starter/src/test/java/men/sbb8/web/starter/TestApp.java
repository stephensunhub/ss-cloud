package men.sbb8.web.starter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import men.sbb8.web.starter.dto.UserInfo;
import men.sbb8.web.starter.dto.resp.RawResponse;
import men.sbb8.web.starter.error.BusinessException;
import men.sbb8.web.starter.error.CommonCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.service.registry.ImportHttpServices;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 等价于 @SpringBootApplication，但组件扫描排除 starter 自己的包：
 * 测试应用恰好位于 starter 的基础包下，不排除的话 starter 的 Bean 会被当作应用自己的 Bean 扫描进来，
 * 自动配置就被绕过了。
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(excludeFilters = {
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = {TypeExcludeFilter.class, AutoConfigurationExcludeFilter.class}),
        @ComponentScan.Filter(type = FilterType.REGEX,
                pattern = "men\\.sbb8\\.web\\.starter\\.(handler|config|resolver|client|redis|json|persistence)\\..*")})
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

        @GetMapping(value = "/plain", produces = "text/plain")
        String plain() {
            return "plain";
        }

        @GetMapping(value = "/json-str", produces = "application/json")
        String jsonStr() {
            return "text";
        }

        /** 下游 404（框架生成）经全局异常处理后对外应是 502，而不是 404 */
        @GetMapping("/relay-missing")
        String relayMissing() {
            demoClient.missing();
            return "unreachable";
        }

        @Autowired
        io.micrometer.tracing.Tracer tracer;

        @GetMapping("/trace-id")
        String traceId() {
            return tracer.currentSpan().context().traceId();
        }

        /** 经声明式客户端调用自己的 /trace-id，验证调用链跨服务串联 */
        @GetMapping("/relay-trace")
        Map<String, String> relayTrace() {
            return Map.of("caller", tracer.currentSpan().context().traceId(),
                    "callee", demoClient.traceId().unwrap());
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
