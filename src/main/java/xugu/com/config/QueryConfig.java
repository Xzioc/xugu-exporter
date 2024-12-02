package xugu.com.config;

import lombok.Data;

/**
 * @Author 熊呈
 * @Mail xiongcheng@xugudb.com
 * @Date 2024/11/28 10:13
 */
@Data
public class QueryConfig {
    private String name;
    private String remark;
    private int interval;
    private boolean enabled;
    private boolean multiRow;
    private String prefix;
    private String sql;


}
