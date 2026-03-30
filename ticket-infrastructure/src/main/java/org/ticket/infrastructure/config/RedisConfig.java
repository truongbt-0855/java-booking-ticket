package org.ticket.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import tools.jackson.databind.ObjectMapper;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Cấu hình Redis cho ứng dụng.
 * Định nghĩa cách serialize/deserialize dữ liệu khi lưu và đọc từ Redis,
 * thay thế JDK serialization mặc định bằng JSON để dễ đọc và debug hơn.
 */
@Configuration
public class RedisConfig {

    /**
     * Tạo RedisTemplate với cấu hình serializer tùy chỉnh.
     * - Key & HashKey: lưu dạng plain String (dễ đọc trên Redis CLI)
     * - Value & HashValue: lưu dạng JSON (dùng Jackson 3.x)
     *
     * @param connectionFactory factory kết nối tới Redis server (host/port từ application.properties)
     * @return RedisTemplate đã được cấu hình
     */
    @Bean
    public RedisTemplate<Object, Object> redisTemplate(RedisConnectionFactory connectionFactory)
    {
        RedisTemplate<Object, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);

        // Serialize value thành JSON dùng Jackson 3.x (thay thế Jackson2JsonRedisSerializer deprecated từ Spring Data Redis 4.0)
        GenericJacksonJsonRedisSerializer serializer = new GenericJacksonJsonRedisSerializer(new ObjectMapper());

        // Key lưu dạng String thường → dễ debug, tránh binary không đọc được
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(serializer);

        // Hash key/value cũng áp dụng cấu hình tương tự (dùng cho HSET/HGET)
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(serializer);

        // Khởi tạo template sau khi set xong toàn bộ config
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }
}
