package cn.ms08.apiconvert.dao;

import cn.ms08.apiconvert.entity.GatewayApiKeyModelEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 网关密钥模型授权 Mapper，管理密钥可调用的对外模型范围。
 */
@Mapper
public interface GatewayApiKeyModelMapper extends BaseMapper<GatewayApiKeyModelEntity> {
}
