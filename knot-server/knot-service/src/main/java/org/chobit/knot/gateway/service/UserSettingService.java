package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.entity.UserSettingEntity;
import org.chobit.knot.gateway.mapper.UserSettingMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserSettingService {

    private final UserSettingMapper userSettingMapper;

    public UserSettingService(UserSettingMapper userSettingMapper) {
        this.userSettingMapper = userSettingMapper;
    }

    public Map<String, String> listSettings(Long userId) {
        List<UserSettingEntity> entities = userSettingMapper.listByUserId(userId);
        Map<String, String> result = new LinkedHashMap<>();
        for (UserSettingEntity entity : entities) {
            result.put(entity.getSettingKey(), entity.getSettingValue());
        }
        return result;
    }

    @Transactional
    public Map<String, String> saveSettings(Long userId, Map<String, String> settings) {
        if (settings == null || settings.isEmpty()) {
            return listSettings(userId);
        }
        for (Map.Entry<String, String> entry : settings.entrySet()) {
            saveSetting(userId, entry.getKey(), entry.getValue());
        }
        return listSettings(userId);
    }

    @Transactional
    public void saveSetting(Long userId, String key, String value) {
        UserSettingEntity entity = new UserSettingEntity();
        entity.setUserId(userId);
        entity.setSettingKey(key);
        entity.setSettingValue(value);
        userSettingMapper.upsert(entity);
    }
}
