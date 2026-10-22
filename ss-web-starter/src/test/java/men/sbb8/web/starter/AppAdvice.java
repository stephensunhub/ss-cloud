package men.sbb8.web.starter;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 模拟应用自己的异常处理（组件扫描注册，不加 @Order），验证它能覆盖 starter 的默认处理
 */
@RestControllerAdvice
class AppAdvice {

    @ExceptionHandler(UnsupportedOperationException.class)
    ResponseEntity<String> handle(UnsupportedOperationException e) {
        return ResponseEntity.status(418).body("APP-HANDLER");
    }
}
