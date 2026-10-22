package men.sbb8.web.starter.dto.resp;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在控制器方法或类上，返回值不做 {@link Resp} 包装
 *
 * @author Stephen
 * @since 2026/06/25 19:31
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RawResponse {}
