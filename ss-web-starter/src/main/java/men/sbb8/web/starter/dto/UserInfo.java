package men.sbb8.web.starter.dto;

/**
 * 网关透传的当前用户上下文，控制器方法声明该类型参数即可自动注入
 *
 * @param channelId   渠道 ID（X-Channel-Id），缺失为 null
 * @param userId      用户 ID（X-User-Id）
 * @param username    用户名（X-User-Name）
 * @param clientType  客户端类型（X-Client）
 * @param language    语言（X-User-Language）
 * @param ip          请求 IP（X-Request-Ip）
 * @param requestTime 请求到达时间，毫秒时间戳
 * @author Stephen
 */
public record UserInfo(Integer channelId,
                       String userId,
                       String username,
                       String clientType,
                       String language,
                       String ip,
                       long requestTime) {
}
