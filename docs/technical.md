# Technical Documentation

## Concurrency

> Chi tiết xem tại [concurrency/cache-and-distributed-lock.md](concurrency/cache-and-distributed-lock.md)

Các kỹ thuật xử lý high concurrency được áp dụng trong project:

| Kỹ thuật | Vấn đề giải quyết |
|---|---|
| Cache-Aside | Giảm tải DB bằng cách cache dữ liệu |
| Distributed Lock (Redisson) | Cache Stampede — nhiều request đồng loạt hit DB khi cache miss |
| Double-check Cache | Tránh query DB dư thừa sau khi lấy được lock |
| Null-value Caching | Cache Penetration — request ID không tồn tại bypass cache mãi mãi |

---

## Redis Serialization

### Tại sao cần `RedisConfig`?

Spring Boot mặc định dùng **JDK serialization** cho `RedisTemplate`. Khi không có `RedisConfig` tùy chỉnh, mọi object lưu vào Redis phải `implements Serializable`, không thì lỗi:

```
ERROR: setObject error: Cannot serialize
```

### JDK vs JSON Serialization

| | JDK Serialization | Jackson JSON |
|---|---|---|
| Output | Binary bytes (không đọc được) | JSON text (đọc được) |
| Yêu cầu | Class phải `implements Serializable` | Chỉ cần getter/field |
| Debug trên Redis CLI | Không thể | Dễ dàng |

### Cách hoạt động

**JDK serialization** đọc vào bộ nhớ nội bộ của object — Java yêu cầu class phải đánh dấu `implements Serializable` để "đồng ý" cho phép điều này.

**Jackson** chỉ đọc các public getter/field rồi ghi ra text JSON — không đụng bộ nhớ nội bộ, không cần `Serializable`.

### `RedisConfig` thay thế serializer mặc định

```java
// Thay JDK serialization bằng JSON (Jackson 3.x — Spring Boot 4.x)
GenericJacksonJsonRedisSerializer serializer = new GenericJacksonJsonRedisSerializer(new ObjectMapper());
redisTemplate.setValueSerializer(serializer);
redisTemplate.setKeySerializer(new StringRedisSerializer()); // key lưu dạng plain String
```

Kết quả trong Redis sau khi có `RedisConfig`:
```
key:   PRO_TICKET:ITEM:1              ← plain String, đọc được
value: {"id":1,"name":"Concert A"}   ← JSON, đọc được
```

---

## Annotations

### @Value

Inject giá trị từ `application.yml` hoặc biến môi trường vào field, không cần đọc config thủ công.

**Cú pháp:**

```java
@Value("${spring.data.redis.host}")
private String redisHost;
```

**Các dạng phổ biến:**

```java
// Đọc từ config — bắt buộc phải có, không có thì lỗi khi start
@Value("${spring.data.redis.host}")
private String host;

// Có giá trị default nếu key không tồn tại (sau dấu :)
@Value("${spring.data.redis.password:}")      // default = rỗng ""
@Value("${spring.data.redis.timeout:3000}")   // default = 3000

// Đọc từ biến môi trường hệ thống
@Value("${DB_PASSWORD}")
private String dbPassword;
```

**Thứ tự ưu tiên khi resolve:**

```
Biến môi trường hệ thống  (export DB_PASSWORD=xxx)
        ↓ nếu không có
file .env                 (spring.config.import)
        ↓ nếu không có
application.yml
        ↓ nếu không có
giá trị default sau dấu :
        ↓ nếu không có
→ lỗi khi khởi động app (BeanCreationException)
```

**Lưu ý quan trọng:** Chỉ hoạt động trong class được Spring quản lý (`@Component`, `@Service`, `@Configuration`...).
`new RedissonConfig()` thủ công → `@Value` không được inject → field = `null`.
