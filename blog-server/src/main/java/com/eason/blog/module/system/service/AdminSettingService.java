package com.eason.blog.module.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eason.blog.common.exception.BusinessException;
import com.eason.blog.module.system.entity.SystemConfig;
import com.eason.blog.module.system.mapper.SystemConfigMapper;
import com.eason.blog.module.system.vo.SystemConfigVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 系统设置：键值对配置的读取与批量更新。
 *
 * <p>只允许更新已存在的 key，避免通过接口往配置表里塞任意键；
 * 公开配置（is_public=1）才会出现在前台 /site/config。</p>
 */
@Service
public class AdminSettingService {

    private final SystemConfigMapper systemConfigMapper;

    public AdminSettingService(SystemConfigMapper systemConfigMapper) {
        this.systemConfigMapper = systemConfigMapper;
    }

    public List<SystemConfigVO> list() {
        return systemConfigMapper.selectList(Wrappers.<SystemConfig>lambdaQuery()
                        .orderByAsc(SystemConfig::getId))
                .stream()
                .map(c -> new SystemConfigVO(c.getId(), c.getConfigKey(), c.getConfigValue(),
                        c.getDescription(), c.getIsPublic()))
                .toList();
    }

    @Transactional
    public int update(Map<String, String> configs) {
        if (configs == null || configs.isEmpty()) {
            return 0;
        }
        int updated = 0;
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            SystemConfig config = systemConfigMapper.selectOne(Wrappers.<SystemConfig>lambdaQuery()
                    .eq(SystemConfig::getConfigKey, entry.getKey())
                    .last("limit 1"));
            if (config == null) {
                throw BusinessException.badRequest("未知配置项：" + entry.getKey());
            }
            String value = entry.getValue() == null ? "" : entry.getValue();
            if (value.length() > 2000) {
                throw BusinessException.badRequest("配置值过长：" + entry.getKey());
            }
            config.setConfigValue(value);
            config.setUpdateTime(LocalDateTime.now());
            systemConfigMapper.updateById(config);
            updated++;
        }
        return updated;
    }
}
