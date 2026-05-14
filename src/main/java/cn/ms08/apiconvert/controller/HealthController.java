package cn.ms08.apiconvert.controller;

import cn.ms08.apiconvert.dao.AiChannelMapper;
import cn.ms08.apiconvert.dao.AiChannelModelMapper;
import cn.ms08.apiconvert.entity.AiChannelModelEntity;
import cn.ms08.apiconvert.service.InstallStatusService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 健康检查接口，返回数据库和核心配置数量。
 */
@RestController
public class HealthController {

    /**
     * 用于执行数据库连通性检查。
     */
    private final JdbcTemplate jdbcTemplate;
    /**
     * 安装状态查询服务。
     */
    private final InstallStatusService installStatusService;
    /**
     * 渠道主表 Mapper。
     */
    private final AiChannelMapper channelMapper;
    /**
     * 渠道模型映射 Mapper。
     */
    private final AiChannelModelMapper modelMapper;

    /**
     * 注入健康检查所需依赖。
     */
    public HealthController(JdbcTemplate jdbcTemplate, InstallStatusService installStatusService,
                            AiChannelMapper channelMapper, AiChannelModelMapper modelMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.installStatusService = installStatusService;
        this.channelMapper = channelMapper;
        this.modelMapper = modelMapper;
    }

    /**
     * 返回服务健康状态和渠道/模型数量。
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        jdbcTemplate.queryForObject("select 1", Integer.class);
        return Map.of(
                "status", "UP",
                "database", "UP",
                "installed", installStatusService.isInstalled(),
                "schemaVersion", installStatusService.currentVersion(),
                "providerCount", channelMapper.selectCount(null),
                "enabledModelCount", modelMapper.selectCount(new LambdaQueryWrapper<AiChannelModelEntity>()
                        .eq(AiChannelModelEntity::getEnabled, true))
        );
    }
}
