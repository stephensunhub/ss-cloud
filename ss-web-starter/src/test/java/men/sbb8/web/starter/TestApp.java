package men.sbb8.web.starter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import men.sbb8.web.starter.dto.UserInfo;
import men.sbb8.web.starter.dto.resp.Resp;
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

    interface DownClient {
        @org.springframework.web.service.annotation.GetExchange("/obj")
        Resp<Map<String, Object>> get();
    }

    interface LbClient {
        @org.springframework.web.service.annotation.GetExchange("/obj")
        Resp<Map<String, Object>> obj();
    }

    interface GhostClient {
        @org.springframework.web.service.annotation.GetExchange("/obj")
        Resp<Map<String, Object>> obj();
    }

    @org.springframework.context.annotation.Configuration
    @ImportHttpServices(group = "down", types = DownClient.class)
    static class DownConfig {
    }

    @org.springframework.context.annotation.Configuration
    @ImportHttpServices(group = "lb", types = LbClient.class)
    static class LbConfig {
    }

    @org.springframework.context.annotation.Configuration
    @ImportHttpServices(group = "ghost", types = GhostClient.class)
    static class GhostConfig {
    }

    @RestController
    static class DemoController {

        @Autowired
        DemoClient demoClient;

        @GetMapping("/obj")
        Resp<Map<String, Object>> obj() {
            return Resp.ok(Map.of("time", LocalDateTime.of(2026, 1, 2, 3, 4, 5), "n", 1));
        }

        @GetMapping("/str")
        Resp<String> str() {
            return Resp.ok("hello");
        }

        @GetMapping("/void")
        Resp<Void> none() {
            return Resp.ok(null);
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
        Resp<String> param(@RequestParam int id) {
            return Resp.ok("x");
        }

        @PostMapping("/valid")
        Resp<String> valid(@Valid @RequestBody Body body) {
            return Resp.ok(body.name());
        }

        @GetMapping("/time")
        Resp<LocalDateTime> time(@RequestParam LocalDateTime at) {
            return Resp.ok(at);
        }

        @PostMapping("/time-body")
        Resp<TimeBody> timeBody(@RequestBody TimeBody body) {
            return Resp.ok(body);
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
        Resp<String> traceId() {
            return Resp.ok(tracer.currentSpan().context().traceId());
        }

        /** 经声明式客户端调用自己的 /trace-id，验证调用链跨服务串联 */
        @GetMapping("/relay-trace")
        Resp<Map<String, String>> relayTrace() {
            return Resp.ok(Map.of("caller", tracer.currentSpan().context().traceId(),
                    "callee", demoClient.traceId().unwrap()));
        }

        @GetMapping("/ip")
        Resp<String> ip() {
            return Resp.ok(men.sbb8.web.starter.utils.WebUtils.getRemoteIp());
        }

        @GetMapping("/uid")
        Resp<Long> uid() {
            return Resp.ok(men.sbb8.web.starter.utils.WebUtils.getCurrentUserId());
        }

        @GetMapping("/page")
        Resp<Map<String, Object>> page(@Valid men.sbb8.web.starter.dto.req.PageReq req) {
            return Resp.ok(Map.of("page", req.getPage(), "size", req.getSize()));
        }

        @GetMapping("/me")
        Resp<UserInfo> me(UserInfo userInfo) {
            return Resp.ok(userInfo);
        }

        /** 经声明式客户端回调自己的 /me，验证用户头透传 */
        @GetMapping("/relay")
        Resp<UserInfo> relay() {
            return Resp.ok(demoClient.me().unwrap());
        }

        @Autowired
        DownClient downClient;

        @Autowired
        LbClient lbClient;

        @Autowired
        GhostClient ghostClient;

        /** 下游根本连不上（连接被拒）经全局异常处理后对外应是 502 */
        @GetMapping("/relay-down")
        Resp<Map<String, Object>> relayDown() {
            return downClient.get();
        }

        /** 按服务名（lb://wallet）经负载均衡调用 */
        @GetMapping("/relay-lb")
        Resp<Map<String, Object>> relayLb() {
            return Resp.ok(lbClient.obj().unwrap());
        }

        /** 按服务名调用一个没有任何实例的服务（lb://ghost）：对外应是 502，而不是 500 */
        @GetMapping("/relay-ghost")
        Resp<Map<String, Object>> relayGhost() {
            return ghostClient.obj();
        }

        /** 下游 5xx 经全局异常处理后对外应是 502 */
        @GetMapping("/relay-boom")
        String relayBoom() {
            demoClient.boom();
            return "unreachable";
        }
    }
}
