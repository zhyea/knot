package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.NotifyTemplateEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface NotificationMapper {
    @Select("select id,code,name,channel,content_tpl,status from notification_templates order by id desc")
    List<NotifyTemplateEntity> listTemplates();

    @Select("select id,code,name,channel,content_tpl,status from notification_templates where code=#{code}")
    NotifyTemplateEntity getTemplateByCode(String code);

    @Insert("insert into notification_templates(code,name,channel,title_tpl,content_tpl,status) values(#{code},#{name},#{channel},#{name},#{contentTpl},#{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertTemplate(NotifyTemplateEntity entity);

    @Insert("insert into notification_records(template_id,receiver,channel,send_status,created_at) values(#{templateId},#{receiver},#{channel},#{sendStatus},now())")
    int insertRecord(@Param("templateId") Long templateId, @Param("receiver") String receiver, @Param("channel") String channel, @Param("sendStatus") String sendStatus);
}
