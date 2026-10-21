package men.sbb8.web.starter;

import men.sbb8.web.starter.dto.UserInfo;
import men.sbb8.web.starter.dto.resp.Resp;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.Map;

interface DemoClient {

    @GetExchange("/obj")
    Resp<Map<String, Object>> obj();

    @GetExchange("/biz")
    Resp<Void> biz();

    @GetExchange("/boom")
    Resp<Void> boom();

    @PostExchange("/valid")
    Resp<String> valid(@RequestBody Map<String, Object> body);

    @GetExchange("/me")
    Resp<UserInfo> me();
}
