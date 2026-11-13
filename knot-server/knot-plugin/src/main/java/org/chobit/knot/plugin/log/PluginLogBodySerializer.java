package org.chobit.knot.plugin.log;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * 正文序列化器：payload 里的 {@code JsonNode} 一律经 {@link PluginLogSupport#body} 出日志。
 *
 * 标注在 body 字段上，六个插件共用同一出口；写的是树而非字符串，结构与字段寻址能力保持不变。
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
