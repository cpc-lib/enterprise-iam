package com.enterprise.iam.common.web.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * 全局默认 long/Long 序列化器：输出十进制字符串。
 *
 * <p>SPEC 47 §9.1（D-1）：所有 64 位标识符在 JSON 中必须为十进制字符串，
 * 避免 JavaScript Number 精度丢失（雪花 ID 超过 2^53）。非 ID 的数值量通过
 * {@link NumericLong} 显式豁免；注解序列化器优先于本模块注册的类型序列化器。
 */
public final class IdAsStringSerializer extends JsonSerializer<Object> {

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
        } else {
            gen.writeString(value.toString());
        }
    }
}
