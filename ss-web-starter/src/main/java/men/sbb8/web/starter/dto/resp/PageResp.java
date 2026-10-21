package men.sbb8.web.starter.dto.resp;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @author Stephe
 */
@Setter
@Getter
@Accessors(chain = true)
public class PageResp<T> {


    private Long total;

    private List<T> data;

    public static <T> PageResp<T> build(Long total, List<T> data){
        PageResp<T> pageResp = new PageResp<>();
        return pageResp.setTotal(total).setData(data);
    }

    public static <T> PageResp<T> of(Long total, List<T> data){
        return build(total,data);
    }
}
