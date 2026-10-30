package org.chobit.knot.plugin.log;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * 正文序列化器：payload 里的 {@code JsonNode} 一律经 {@link PluginLogSupport#body} 出日志。
 *
 * <p>挂在字段上而不是让六个插件各自处理，是为了让策略只有一个出口——
 * 任何一个新 payload 忘了处理就直接泄露，正是这类缺陷最容易复发的地方。</p>
 *
 * <p>写的是<b>树</b>不是字符串：正文的结构与字段寻址能力是既有契约，不能被脱敏顺手抹掉。</p>
 */
public final class PluginLogBodySerializer extends JsonSerializer<JsonNode> {

    @Override
    public void serialize(JsonNode value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        JsonNode body = PluginLogSupport.body(value);
        if (body == null) {
            gen.writeNull();
            return;
        }
        gen.writeTree(body);
    }
}
