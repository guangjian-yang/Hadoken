package pers.guangjian.hadoken.infra.mq.core.message;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * @Author: yanggj
 * @Description: Redis 消息抽象基类
 * @Date: 2022/03/02 11:50
 * @Version: 1.0.0
 */
@Data
public abstract class AbstractRedisMessage {

    /**
     * 头
     */
    private Map<String, String> headers = new HashMap<>();

    public String getHeader(String key) {
        return headers.get(key);
    }

    public void addHeader(String key, String value) {
        headers.put(key, value);
    }

}
