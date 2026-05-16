package cn.ms08.apiconvert.dao;

import cn.ms08.apiconvert.entity.GatewaySystemConfigEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 网关系统配置表 Mapper，供管理端和路由服务读取运行期配置。
 */
@Mapper
public interface GatewaySystemConfigMapper extends BaseMapper<GatewaySystemConfigEntity> {
}
