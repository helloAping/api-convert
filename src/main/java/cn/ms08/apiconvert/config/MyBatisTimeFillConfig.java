package cn.ms08.apiconvert.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * MyBatis-Plus 时间字段自动填充配置，统一使用项目指定时区生成业务时间。
 */
@Component
public class MyBatisTimeFillConfig implements MetaObjectHandler {

    /**
     * 项目统一时区，默认由 DateTimeConfig 提供 Asia/Shanghai。
     */
    private final ZoneId projectZoneId;

    /**
     * 注入项目时区，避免直接使用系统默认时区。
     */
    public MyBatisTimeFillConfig(ZoneId projectZoneId) {
        this.projectZoneId = projectZoneId;
    }

    /**
     * 新增记录时填充 createdAt 和 updatedAt。
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now(projectZoneId);
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
    }

    /**
     * 更新记录时刷新 updatedAt。
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now(projectZoneId));
    }
}
