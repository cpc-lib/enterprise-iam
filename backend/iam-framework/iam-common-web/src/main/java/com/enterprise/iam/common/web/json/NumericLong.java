package com.enterprise.iam.common.web.json;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个 long/Long 属性虽然命中全局「ID 序列化为十进制字符串」规则，
 * 但语义上是数值量（如 version、expiresIn、partSize、epoch），必须序列化为 JSON number。
 *
 * <p>SPEC 47 §9.1 / OpenAPI：所有 64 位标识符为十进制字符串；非 ID 的整数仍为 number。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.METHOD, ElementType.PARAMETER})
@JacksonAnnotationsInside
@JsonSerialize(using = NumericLongSerializer.class)
public @interface NumericLong {
}
