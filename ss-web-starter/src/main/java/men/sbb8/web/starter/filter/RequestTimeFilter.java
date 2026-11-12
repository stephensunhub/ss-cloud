package men.sbb8.web.starter.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import men.sbb8.web.starter.utils.RequestTime;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 请求一进来就记下到达时间（请求属性 {@link RequestTime#ATTRIBUTE}），信封 {@code Resp} 的 requestTime 用它
 *
 * @author Stephen
 */
public class RequestTimeFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getAttribute(RequestTime.ATTRIBUTE) == null) {
            request.setAttribute(RequestTime.ATTRIBUTE, System.currentTimeMillis());
        }
        chain.doFilter(request, response);
    }
}
