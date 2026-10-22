package men.sbb8.web.starter.config;

import men.sbb8.web.starter.json.JacksonUtils;
import men.sbb8.web.starter.json.SsJacksonCustomizer;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.json.JsonMapper;

/**
 * 在 Boot 的 JsonMapper 之上叠加项目统一的 Jackson 约定，并把该 mapper 交给 {@link JacksonUtils}
 *
 * @author Stephen
 */
@AutoConfiguration(before = JacksonAutoConfiguration.class)
@ConditionalOnClass(JsonMapper.class)
public class SsJacksonAutoConfiguration {

    @Bean
    public SsJacksonCustomizer ssJacksonCustomizer() {
        return new SsJacksonCustomizer();
    }

    @Bean
    public JacksonUtilsBinding ssJacksonUtilsBinding(JsonMapper jsonMapper) {
        return new JacksonUtilsBinding(jsonMapper);
    }

    /**
     * 容器启动时把容器的 mapper 交给 {@link JacksonUtils}，关闭时还原
     */
    public static final class JacksonUtilsBinding implements InitializingBean, DisposableBean {

        private final JsonMapper jsonMapper;

        JacksonUtilsBinding(JsonMapper jsonMapper) {
            this.jsonMapper = jsonMapper;
        }

        @Override
        public void afterPropertiesSet() {
            JacksonUtils.init(jsonMapper);
        }

        @Override
        public void destroy() {
            JacksonUtils.release(jsonMapper);
        }
    }
}
