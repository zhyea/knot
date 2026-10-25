package org.chobit.knot.dal.mapper;

import org.chobit.knot.dal.entity.NotifyTemplateEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NotificationMapper {

    List<NotifyTemplateEntity> listTemplates(@Param("keyword") String keyword);

    NotifyTemplateEntity getTemplateByCode(String code);

    int insertTemplate(NotifyTemplateEntity entity);
}
