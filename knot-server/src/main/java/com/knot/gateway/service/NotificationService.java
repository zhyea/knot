package com.knot.gateway.service;

import com.knot.gateway.entity.NotifyTemplateEntity;
import com.knot.gateway.mapper.NotificationMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class NotificationService {
    private final NotificationMapper notificationMapper;

    public NotificationService(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    public List<TemplateDto> listTemplates() {
        return notificationMapper.listTemplates().stream()
                .map(t -> new TemplateDto(t.getId(), t.getCode(), t.getName(), t.getChannel(), t.getContentTpl()))
                .toList();
    }

    public TemplateDto createTemplate(TemplateDto request) {
        NotifyTemplateEntity e = new NotifyTemplateEntity();
        e.setCode(request.code());
        e.setName(request.name());
        e.setChannel(request.channel());
        e.setContentTpl(request.content());
        e.setStatus("ACTIVE");
        notificationMapper.insertTemplate(e);
        return new TemplateDto(e.getId(), e.getCode(), e.getName(), e.getChannel(), e.getContentTpl());
    }

    public SendResultDto send(String templateCode, List<String> receivers, Map<String, String> vars) {
        TemplateDto template = listTemplates().stream().filter(t -> t.code().equals(templateCode)).findFirst()
                .orElse(new TemplateDto(0L, templateCode, templateCode, "EMAIL", ""));
        for (String receiver : receivers) {
            notificationMapper.insertRecord(template.id(), receiver, template.channel(), "SENT");
        }
        return new SendResultDto("ntf_" + System.currentTimeMillis(), "SENT", receivers.size());
    }

    public record TemplateDto(Long id, String code, String name, String channel, String content) {}
    public record SendResultDto(String taskId, String status, int receiverCount) {}
}
