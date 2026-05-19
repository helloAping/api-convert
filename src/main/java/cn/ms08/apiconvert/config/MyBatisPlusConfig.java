package cn.ms08.apiconvert.config;

import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class MyBatisPlusConfig {

    /**
     * 配置 MyBatis-Plus 的驼峰映射、自动填充处理器和分页拦截器插件。
     */
    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource, MetaObjectHandler metaObjectHandler,
                                               MybatisPlusInterceptor mybatisPlusInterceptor) throws Exception {
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setPlugins(new Interceptor[]{mybatisPlusInterceptor});
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        factoryBean.setConfiguration(configuration);
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setMetaObjectHandler(metaObjectHandler);
        factoryBean.setGlobalConfig(globalConfig);
        return factoryBean.getObject();
    }

    /**
     * MyBatis-Plus 分页拦截器，自动检测 SQLite/MySQL 方言，为 selectPage 查询拼接 LIMIT/OFFSET。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        return interceptor;
    }
}
