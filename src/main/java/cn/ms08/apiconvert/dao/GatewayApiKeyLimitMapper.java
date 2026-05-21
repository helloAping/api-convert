package cn.ms08.apiconvert.dao;

import cn.ms08.apiconvert.entity.GatewayApiKeyLimitEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 网关密钥限制项 Mapper，管理额度、请求数等滑动窗口限制。
 */
@Mapper
public interface GatewayApiKeyLimitMapper extends BaseMapper<GatewayApiKeyLimitEntity> {
}
