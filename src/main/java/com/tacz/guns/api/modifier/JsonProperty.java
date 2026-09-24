package com.tacz.guns.api.modifier;

import javax.annotation.Nullable;

/**
 * 配件从 Json 读取的数据
 *
 * @param <T> Json 读取后转换成的中间数据类型
 */
public class JsonProperty<T> {
    private @Nullable T value;

    public JsonProperty(@Nullable T value) {
        this.value = value;
    }

    @Nullable
    public T getValue() {
        return value;
    }

    public void setValue(@Nullable T value) {
        this.value = value;
    }

}
