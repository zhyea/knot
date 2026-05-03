package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.NotifyTemplateEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NotificationMapper {

    List<NotifyTemplateEntity> listTemplates();

    NotifyTemplateEntity getTemplateByCode(String code);

    int insertTemplate(NotifyTemplateEntity entity);

    int insertRecord(@Param("templateId") Long templateId,
                     @Param("receiver") String receiver,
                     @Param("channel") String channel,
                     @Param("sendStatus") String sendStatus);
}
