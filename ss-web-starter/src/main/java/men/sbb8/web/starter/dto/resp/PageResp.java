package men.sbb8.web.starter.dto.resp;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 分页结果：total 为符合条件的总条数，data 为当前页的内容
 *
 * @author Stephen
 */
@Setter
@Getter
@Accessors(chain = true)
public class PageResp<T> {


    private Long total;

    private List<T> data;

    public static <T> PageResp<T> of(Long total, List<T> data) {
        return new PageResp<T>().setTotal(total).setData(data);
    }
}
