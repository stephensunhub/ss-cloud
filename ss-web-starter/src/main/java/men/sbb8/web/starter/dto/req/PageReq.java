package men.sbb8.web.starter.dto.req;

import lombok.Getter;
import lombok.Setter;

/**
 * 分页查询父类
 * 需要分页查询的请求需要继承此类
 *
 * @author Stephen
 */
@Setter
@Getter
public class PageReq implements BaseReq {


    private Integer page = 1;

    private Integer size = 20;

}
