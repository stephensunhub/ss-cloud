package men.sbb8.web.starter.dto.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 分页查询父类
 * 需要分页查询的请求需要继承此类，并在控制器参数上加 {@code @Valid}，size 上限 {@value #MAX_SIZE}
 *
 * @author Stephen
 */
@Setter
@Getter
public class PageReq implements BaseReq {


    public static final int MAX_SIZE = 200;

    @NotNull(message = "page 不能为空")
    @Min(value = 1, message = "page 必须大于等于 1")
    private Integer page = 1;

    @NotNull(message = "size 不能为空")
    @Min(value = 1, message = "size 必须大于等于 1")
    @Max(value = MAX_SIZE, message = "size 不能超过 " + MAX_SIZE)
    private Integer size = 20;

}
