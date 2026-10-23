package men.sbb8.web.starter.config;

import men.sbb8.web.starter.utils.SpringBeans;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;

/**
 * 把容器交给 {@link SpringBeans}，容器关闭时释放
 *
 * @author Stephen
 */
@AutoConfiguration
public class SsSpringBeansAutoConfiguration {

    @Bean
    public static SpringBeansBinding ssSpringBeansBinding() {
        return new SpringBeansBinding();
    }

    public static final class SpringBeansBinding implements ApplicationContextAware, DisposableBean {

        private ApplicationContext context;

        @Override
        public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
            this.context = applicationContext;
            SpringBeans.init(applicationContext);
        }

        @Override
        public void destroy() {
            SpringBeans.release(context);
        }
    }
}
