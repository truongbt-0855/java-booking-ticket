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
