package cn.ms08.apiconvert.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.DialectFactory;
import com.baomidou.mybatisplus.extension.plugins.pagination.dialects.IDialect;
import com.baomidou.mybatisplus.extension.plugins.pagination.dialects.MySqlDialect;
import com.baomidou.mybatisplus.extension.toolkit.JdbcUtils;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MyBatis-Plus 分页拦截器实现，在社区版 3.5.16 中 PaginationInnerInterceptor 未内置，
 * 此实现通过 JdbcUtils 自动检测数据库方言（SQLite/MySQL），为 selectPage 查询拼接 LIMIT/OFFSET。
 */
public class PaginationInnerInterceptor implements InnerInterceptor {

    private static final Field BOUND_SQL_FIELD;
    private static final Field PARAMETER_MAPPINGS_FIELD;

    static {
        try {
            BOUND_SQL_FIELD = BoundSql.class.getDeclaredField("sql");
            BOUND_SQL_FIELD.setAccessible(true);
            PARAMETER_MAPPINGS_FIELD = BoundSql.class.getDeclaredField("parameterMappings");
            PARAMETER_MAPPINGS_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("BoundSql pagination fields not found", e);
        }
    }

    @Override
    public boolean willDoQuery(Executor executor, MappedStatement ms, Object parameter,
                               RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
        return true;
    }

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter,
                            RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
        IPage<?> page = findPage(parameter);
        if (page == null) {
            return;
        }

        long size = page.getSize();
        if (size <= 0) {
            return;
        }

        long current = Math.max(1, page.getCurrent());
        long offset = (current - 1) * size;

        // 自动检测数据库类型，获取对应方言的分页 SQL 构建器
        DbType dbType = JdbcUtils.getDbType(executor);
        IDialect dialect = DialectFactory.getDialect(dbType);
        // SQLite 在 DialectFactory 中没有注册方言，其语法与 MySQL 兼容（LIMIT ?,?）
        if (dialect == null) {
            dialect = new MySqlDialect();
        }

        var model = dialect.buildPaginationSql(boundSql.getSql(), Math.max(0, offset), size);

        try {
            // 通过反射替换 BoundSql 中的原始 SQL 为带 LIMIT/OFFSET 的 SQL
            BOUND_SQL_FIELD.set(boundSql, model.getDialectSql());
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to set pagination SQL on BoundSql", e);
        }

        // 注册分页参数占位符（mybatis_plus_first / mybatis_plus_second）到 ParameterMapping 和附加参数
        List<ParameterMapping> parameterMappings = new ArrayList<>(boundSql.getParameterMappings());
        // MyBatis 3.5.19 会把部分 ParameterMapping 列表暴露为不可变集合，分页参数需要写入新的可变副本。
        model.consumers(parameterMappings, ms.getConfiguration(), boundSql.getAdditionalParameters());
        setParameterMappings(boundSql, parameterMappings);
    }

    /**
     * 从查询参数中查找 IPage 实例，支持直接传递 Page 对象或放在 Map 参数中。
     */
    private IPage<?> findPage(Object parameter) {
        if (parameter instanceof IPage<?> page) {
            return page;
        }
        if (parameter instanceof Map<?, ?> paramMap) {
            for (Object value : paramMap.values()) {
                if (value instanceof IPage<?> page) {
                    return page;
                }
            }
        }
        return null;
    }

    /**
     * 将追加了分页占位符的参数映射写回 BoundSql，避免直接修改不可变集合导致分页查询失败。
     */
    private void setParameterMappings(BoundSql boundSql, List<ParameterMapping> parameterMappings) {
        try {
            PARAMETER_MAPPINGS_FIELD.set(boundSql, parameterMappings);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to set pagination parameter mappings on BoundSql", e);
        }
    }
}
