package men.sbb8.web.starter.utils;

import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/**
 * 静态取 Spring Bean，给无法注入的地方使用（MyBatis 的 TypeHandler、静态方法、框架反射创建的对象等）；
 * 能注入的地方直接注入，不要用它。
 *
 * <p>只能取已存在的 Bean，不提供运行时注册 Bean（原生镜像里运行时新增的 Bean 定义没有对应的反射元数据）。
 * 容器由 {@code SsSpringBeansAutoConfiguration} 在启动时交给它，关闭时释放。
 * 这是进程级的全局状态：同一个 JVM 里有多个容器时，用最后启动且仍在运行的那个。
 * 容器未启动或已关闭时调用抛 {@link IllegalStateException}。
 *
 * @author Stephen
 */
public final class SpringBeans {

    /**
     * 正在运行的容器，按启动先后排列；正常的服务只有一个，测试或父子容器里可能有多个
     */
    private static final Deque<ApplicationContext> BOUND = new ArrayDeque<>();

    /**
     * 读取很频繁、写入只在容器启停时，所以读不加锁，用 volatile
     */
    private static volatile ApplicationContext current;

    private SpringBeans() {
    }

    /**
     * 由自动配置在容器启动时调用，业务代码不要调用
     */
    public static synchronized void init(ApplicationContext context) {
        BOUND.add(context);
        current = context;
    }

    /**
     * 由自动配置在容器关闭时调用，业务代码不要调用：回到仍在运行的、最后启动的那个容器，没有则置空，
     * 不继续引用已关闭的容器
     */
    public static synchronized void release(ApplicationContext context) {
        BOUND.remove(context);
        current = BOUND.peekLast();
    }

    public static <T> T getBean(Class<T> type) {
        return context().getBean(type);
    }

    public static <T> T getBean(String name, Class<T> type) {
        return context().getBean(name, type);
    }

    public static <T> Map<String, T> getBeans(Class<T> type) {
        return context().getBeansOfType(type);
    }

    public static Environment getEnvironment() {
        return context().getEnvironment();
    }

    private static ApplicationContext context() {
        ApplicationContext context = current;
        if (context == null) {
            throw new IllegalStateException("Spring 容器尚未启动或已关闭，无法取 Bean");
        }
        return context;
    }
}
