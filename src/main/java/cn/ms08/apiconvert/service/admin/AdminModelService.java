package cn.ms08.apiconvert.service.admin;

import cn.ms08.apiconvert.dao.AiChannelModelMapper;
import cn.ms08.apiconvert.dto.admin.ModelEnabledForm;
import cn.ms08.apiconvert.dto.admin.ModelQuotaForm;
import cn.ms08.apiconvert.entity.AiChannelModelEntity;
import cn.ms08.apiconvert.exception.ErrorCode;
import cn.ms08.apiconvert.exception.GatewayException;
import cn.ms08.apiconvert.vo.admin.ModelVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端模型服务，负责汇总所有渠道保存的模型映射。
 */
@Service
public class AdminModelService {

    /**
     * 读取渠道模型映射表；模型写入统一由渠道管理完成。
     */
    private final AiChannelModelMapper modelMapper;

    /**
     * 注入渠道模型映射 Mapper。
     */
    public AdminModelService(AiChannelModelMapper modelMapper) {
        this.modelMapper = modelMapper;
    }

    /**
     * 按对外模型名聚合所有渠道保存的模型，重复模型只返回一次。
     */
    public List<ModelVO> list() {
        List<AiChannelModelEntity> models = modelMapper.selectList(new LambdaQueryWrapper<AiChannelModelEntity>()
                .orderByAsc(AiChannelModelEntity::getPublicName)
                .orderByAsc(AiChannelModelEntity::getChannelCode)
                .orderByAsc(AiChannelModelEntity::getId));
        Map<String, List<AiChannelModelEntity>> grouped = new LinkedHashMap<>();
        for (AiChannelModelEntity model : models) {
            grouped.computeIfAbsent(model.getPublicName(), ignored -> new java.util.ArrayList<>()).add(model);
        }
        return grouped.values().stream().map(this::toAggregatedVO).toList();
    }

    /**
     * 按记录 ID 查询单条模型映射。
     */
    public ModelVO getById(Long id) {
        var entity = modelMapper.selectById(id);
        if (entity == null) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.NOT_FOUND, "模型不存在");
        }
        return toSingleVO(entity);
    }

    /**
     * 更新同一对外模型名下所有渠道映射的额度单价，确保随机路由命中任一渠道时扣费规则一致。
     */
    @Transactional
    public ModelVO updateQuota(Long id, ModelQuotaForm form) {
        var entity = modelMapper.selectById(id);
        if (entity == null) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.NOT_FOUND, "模型不存在");
        }
        List<AiChannelModelEntity> samePublicName = modelMapper.selectList(new LambdaQueryWrapper<AiChannelModelEntity>()
                .eq(AiChannelModelEntity::getPublicName, entity.getPublicName()));
        for (AiChannelModelEntity model : samePublicName) {
            model.setInputQuotaPerMillion(form.inputQuotaPerMillion());
            model.setOutputQuotaPerMillion(form.outputQuotaPerMillion());
            model.setCacheReadQuotaPerMillion(form.cacheReadQuotaPerMillion());
            modelMapper.updateById(model);
        }
        return toAggregatedVO(samePublicName);
    }

    /**
     * 启用或关闭同一对外模型名下的所有渠道映射，确保公开模型列表和路由行为一致。
     */
    @Transactional
    public ModelVO updateEnabled(Long id, ModelEnabledForm form) {
        var entity = modelMapper.selectById(id);
        if (entity == null) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.NOT_FOUND, "模型不存在");
        }
        if (form.enabled() == null) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "模型启用状态不能为空");
        }
        List<AiChannelModelEntity> samePublicName = modelMapper.selectList(new LambdaQueryWrapper<AiChannelModelEntity>()
                .eq(AiChannelModelEntity::getPublicName, entity.getPublicName()));
        for (AiChannelModelEntity model : samePublicName) {
            model.setEnabled(form.enabled());
            modelMapper.updateById(model);
        }
        return toAggregatedVO(samePublicName);
    }

    /**
     * 将同名模型的多条渠道记录聚合为一个管理端模型视图。
     */
    private ModelVO toAggregatedVO(List<AiChannelModelEntity> models) {
        AiChannelModelEntity first = models.getFirst();
        return new ModelVO(
                first.getId(),
                first.getPublicName(),
                first.getChannelCode(),
                first.getProviderModel(),
                first.getCapabilitiesJson(),
                models.stream().anyMatch(model -> Boolean.TRUE.equals(model.getEnabled())),
                (long) models.size(),
                models.stream().map(AiChannelModelEntity::getChannelCode).distinct().toList(),
                models.stream().map(AiChannelModelEntity::getProviderModel).distinct().toList(),
                first.getInputQuotaPerMillion(),
                first.getOutputQuotaPerMillion(),
                first.getCacheReadQuotaPerMillion()
        );
    }

    /**
     * 将单条模型映射转换为与聚合视图字段兼容的 VO。
     */
    private ModelVO toSingleVO(AiChannelModelEntity entity) {
        return new ModelVO(
                entity.getId(),
                entity.getPublicName(),
                entity.getChannelCode(),
                entity.getProviderModel(),
                entity.getCapabilitiesJson(),
                entity.getEnabled(),
                1L,
                List.of(entity.getChannelCode()),
                List.of(entity.getProviderModel()),
                entity.getInputQuotaPerMillion(),
                entity.getOutputQuotaPerMillion(),
                entity.getCacheReadQuotaPerMillion()
        );
    }
}
