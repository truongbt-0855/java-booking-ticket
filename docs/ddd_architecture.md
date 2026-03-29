# DDD Architecture — Booking Ticket Project

## Mục lục
1. [DDD là gì và tại sao dùng nó?](#1-ddd-là-gì-và-tại-sao-dùng-nó)
2. [Vấn đề của MVC truyền thống](#2-vấn-đề-của-mvc-truyền-thống)
3. [Các layer trong project](#3-các-layer-trong-project)
4. [Dependency direction — ai phụ thuộc ai?](#4-dependency-direction--ai-phụ-thuộc-ai)
5. [Flow thực tế trong project này](#5-flow-thực-tế-trong-project-này)
6. [Giải thích từng file trong flow](#6-giải-thích-từng-file-trong-flow)
7. [Tại sao Domain không được biết Infrastructure?](#7-tại-sao-domain-không-được-biết-infrastructure)
8. [TicketDetailCacheService — vùng xám của kiến trúc](#8-ticketdetailcacheservice--vùng-xám-của-kiến-trúc)
9. [Các thành phần cốt lõi của DDD](#9-các-thành-phần-cốt-lõi-của-ddd)
10. [Trạng thái hiện tại và còn thiếu gì](#10-trạng-thái-hiện-tại-và-còn-thiếu-gì)

---

## 1. DDD là gì và tại sao dùng nó?

**Domain-Driven Design (DDD)** là cách tổ chức code xoay quanh **nghiệp vụ (business logic)** thay vì xoay quanh công nghệ.

Nghĩ đơn giản: khi bạn đặt vé xem concert, có những **luật nghiệp vụ** cố định:
- Vé đã dùng thì không huỷ được
- Không đủ vé thì không được đặt
- Flash sale chỉ diễn ra trong khung giờ nhất định

Những luật này **không phụ thuộc vào công nghệ** — MySQL hay MongoDB, REST hay gRPC, Redis hay Memcached, luật vẫn là luật. DDD tách các luật đó ra thành một layer riêng (Domain), không để công nghệ làm bẩn nó.

---

## 2. Vấn đề của MVC truyền thống

### MVC trông như thế này

```
Controller → Service → Repository (JPA) → Database
```

Trong thực tế, Service của MVC thường trở thành **God Service** — cái gì cũng nhét vào:

```java
@Service
public class TicketService {

    @Autowired
    private JpaTicketRepository repo;     // biết cụ thể là JPA
    @Autowired
    private EmailService emailService;    // biết cách gửi email
    @Autowired
    private RedisTemplate redis;          // biết cả Redis

    public void bookTicket(Long eventId, Long userId, int quantity) {
        // validate
        // kiểm tra còn vé không
        // lưu DB
        // gửi email
        // set cache
        // tất cả nhồi vào 1 chỗ
    }
}
```

**Hệ quả:**
- Muốn đổi MySQL → MongoDB → phải sửa Service
- Muốn test logic "còn vé không" → phải mock DB, mock Redis, mock Email
- Team lớn → 3 người cùng sửa 1 file Service → conflict

---

## 3. Các layer trong project

Project có **5 Maven module**, mỗi module là 1 layer với trách nhiệm riêng biệt:

```
booking_ticket/
├── ticket-domain           # Luật nghiệp vụ thuần tuý — không biết DB, Redis, HTTP là gì
├── ticket-application      # Nhạc trưởng — điều phối luồng, không chứa business logic
├── ticket-infrastructure   # Kỹ thuật: DB, Redis, Kafka... implement interface của domain
├── ticket-controller       # HTTP endpoint — nhận request, trả response
└── ticket-start            # Điểm khởi động Spring Boot duy nhất
```

### Nguyên tắc của từng layer

| Layer | Được phép làm | Không được phép |
|---|---|---|
| domain | Business logic, định nghĩa interface | Import từ infra, dùng JPA, dùng Redis |
| application | Gọi domain + infra, quản lý flow | Chứa business logic |
| infrastructure | Implement interface của domain, dùng JPA/Redis | Chứa business logic |
| controller | Nhận HTTP, gọi AppService, trả response | Gọi thẳng domain hoặc infra |
| start | `SpringApplication.run()` | Mọi thứ khác |

---

## 4. Dependency direction — ai phụ thuộc ai?

```
ticket-domain           ← không phụ thuộc ai (đứng độc lập)
      ▲
      │
ticket-infrastructure  ← phụ thuộc: ticket-domain
      ▲
      │
ticket-application     ← phụ thuộc: ticket-domain + ticket-infrastructure
      ▲
      │
ticket-controller      ← phụ thuộc: ticket-application
      ▲
      │
ticket-start           ← phụ thuộc: ticket-controller (kéo theo tất cả)
```

**Quy tắc bất di bất dịch: mũi tên chỉ đi VÀO domain, không bao giờ đi ra.**

Domain không biết ai đang dùng nó. Nó chỉ định nghĩa interface và business logic. Đây là lý do tại sao khi đổi công nghệ (MySQL → MongoDB), bạn chỉ sửa infrastructure, không đụng đến domain.

---

## 5. Flow thực tế trong project này

Khi client gọi `GET /ticket/1/detail/2`:

```
[HTTP Request] GET /ticket/1/detail/2
        │
        ▼
[ticket-controller]
TicketDetailController.getTicketDetail(ticketId=1, detailId=2)
  - Log request
  - Gọi ticketDetailAppService.getTicketDetailById(2)
        │
        ▼
[ticket-application]
TicketDetailAppServiceImpl.getTicketDetailById(2)
  - Điều phối: dùng cache hay không?
  - Gọi ticketDetailCacheService.getTicketDefaultCacheVip(2, timestamp)
        │
        ▼
[ticket-application - cache orchestration]
TicketDetailCacheService.getTicketDefaultCacheVip(2, timestamp)
  - Lấy distributed lock từ Redisson (tránh cache stampede)
  - Check Redis cache trước
  - Nếu cache miss → gọi domain để lấy từ DB
  - Set Redis cache
        │                          │
        ▼                          ▼
[ticket-infrastructure]    [ticket-domain]
RedisInfraService          TicketDetailDomainService
  - get/set Redis             .getTicketDetailById(2)
                                    │
                                    ▼
                            [ticket-domain]
                            TicketDetailRepository (interface)
                            .findById(2)
                                    │
                                    ▼
                            [ticket-infrastructure]
                            TicketDetailInfraRepositoryImpl
                              - Implement interface của domain
                              - Gọi TicketDetailJPAMapper
                                    │
                                    ▼
                            [Spring Data JPA]
                            TicketDetailJPAMapper extends JpaRepository
                                    │
                                    ▼
                               MySQL Database
```

---

## 6. Giải thích từng file trong flow

### 6.1 Controller — `TicketDetailController.java`

```java
@RestController
@RequestMapping("/ticket")
@Slf4j
@RequiredArgsConstructor
public class TicketDetailController {

    private final TicketDetailAppService ticketDetailAppService; // chỉ biết interface

    @GetMapping("/{ticketId}/detail/{detailId}")
    public ResultMessage<TicketDetail> getTicketDetail(
            @PathVariable("ticketId") Long ticketId,
            @PathVariable("detailId") Long detailId
    ) {
        return ResultUtil.data(ticketDetailAppService.getTicketDetailById(detailId));
    }
}
```

**Nhận xét:**
- ✅ Chỉ inject `TicketDetailAppService` (interface, không phải impl)
- ✅ Không chứa business logic
- ✅ Dùng `@RequiredArgsConstructor` + `final` → Spring tự inject
- Controller không biết cache, không biết DB, không biết domain service

---

### 6.2 AppService — `TicketDetailAppServiceImpl.java`

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class TicketDetailAppServiceImpl implements TicketDetailAppService {

    private final TicketDetailDomainService ticketDetailDomainService;
    private final TicketDetailCacheService ticketDetailCacheService;

    @Override
    public TicketDetail getTicketDetailById(Long ticketId) {
        // Quyết định strategy: dùng cache VIP hay không
        return ticketDetailCacheService.getTicketDefaultCacheVip(ticketId, System.currentTimeMillis());
    }
}
```

**Nhận xét:**
- ✅ Implements interface `TicketDetailAppService`
- ✅ Không chứa business logic — chỉ điều phối
- ✅ Đây là nơi quyết định "dùng cache strategy nào" — đúng trách nhiệm của Application layer

---

### 6.3 CacheService — `TicketDetailCacheService.java`

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class TicketDetailCacheService {
    private final RedisDistributedService redisDistributedService; // từ infra
    private final RedisInfraService redisInfraService;             // từ infra
    private final TicketDetailDomainService ticketDetailDomainService; // từ domain

    public TicketDetail getTicketDefaultCacheVip(Long id, Long version) {
        // 1. Check Redis trước (không cần lock)
        TicketDetail ticketDetail = ticketDetailDomainService.getTicketDetailById(id);
        if (ticketDetail != null) return ticketDetail;

        // 2. Cache miss → lấy distributed lock để tránh nhiều thread cùng query DB
        RedisDistributedLocker locker = redisDistributedService.getDistributedLock("PRO_LOCK_KEY_ITEM" + id);
        try {
            boolean isLock = locker.tryLock(1, 5, TimeUnit.SECONDS);
            if (!isLock) return ticketDetail; // không lấy được lock → trả null, client retry

            // 3. Check cache lần 2 (thread khác có thể đã set rồi)
            ticketDetail = redisInfraService.getObject(genEventItemKey(id), TicketDetail.class);
            if (ticketDetail != null) return ticketDetail;

            // 4. Vẫn không có → query DB
            ticketDetail = ticketDetailDomainService.getTicketDetailById(id);

            // 5. Set cache dù DB trả null hay không (tránh cache penetration)
            redisInfraService.setObject(genEventItemKey(id), ticketDetail);
            return ticketDetail;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            locker.unlock(); // LUÔN LUÔN unlock dù thành công hay thất bại
        }
    }
}
```

**Pattern này giải quyết 2 vấn đề phổ biến:**

**Cache Stampede (Thundering Herd):** Khi cache hết hạn, hàng trăm request cùng lúc đổ vào DB. Distributed lock đảm bảo chỉ 1 thread query DB, các thread còn lại chờ hoặc trả về null.

**Cache Penetration:** Nếu key không tồn tại trong DB, mỗi request đều bypass cache và query DB. Fix bằng cách set cache kể cả khi DB trả null (cache null value).

> Xem thêm phần [8. TicketDetailCacheService — vùng xám](#8-ticketdetailcacheservice--vùng-xám-của-kiến-trúc) để hiểu tại sao file này đặt ở Application layer lại là vùng xám.

---

### 6.4 DomainService — `TicketDetailDomainServiceImpl.java`

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class TicketDetailDomainServiceImpl implements TicketDetailDomainService {

    private final TicketDetailRepository ticketDetailRepository; // interface, không biết impl là JPA hay gì

    @Override
    public TicketDetail getTicketDetailById(Long ticketId) {
        return ticketDetailRepository.findById(ticketId).orElse(null);
    }
}
```

**Nhận xét:**
- ✅ Chỉ phụ thuộc vào `TicketDetailRepository` (interface trong domain)
- ✅ Không biết gì về JPA, MySQL, Redis
- ✅ Đây là nơi sẽ đặt business logic khi có: "còn vé không?", "có trong thời gian flash sale không?"...
- Hiện tại business logic chưa có vì project đang trong giai đoạn scaffold

---

### 6.5 Repository Interface — `TicketDetailRepository.java`

```java
// Trong ticket-domain — đây là CONTRACT
public interface TicketDetailRepository {
    Optional<TicketDetail> findById(Long id);
}
```

**Đây là chìa khoá của DDD.** Domain không biết "tao sẽ lấy data từ đâu" — nó chỉ tuyên bố "tao CẦN khả năng findById". Ai implement, implement thế nào, domain không quan tâm.

---

### 6.6 Infrastructure Repository — `TicketDetailInfraRepositoryImpl.java`

```java
// Trong ticket-infrastructure — đây là IMPLEMENTATION
@Service
@Slf4j
@RequiredArgsConstructor
public class TicketDetailInfraRepositoryImpl implements TicketDetailRepository {

    private final TicketDetailJPAMapper ticketDetailJPAMapper; // Spring Data JPA

    @Override
    public Optional<TicketDetail> findById(Long id) {
        log.info("Implement Infrastructure : {}", id);
        return ticketDetailJPAMapper.findById(id); // gọi JPA
    }
}
```

**Đây là Adapter Pattern:** Domain định nghĩa interface → Infrastructure adapt JPA vào interface đó. Nếu mai đổi MongoDB, chỉ cần viết `TicketDetailMongoRepositoryImpl implements TicketDetailRepository` mà không đụng đến domain.

---

### 6.7 JPA Mapper — `TicketDetailJPAMapper.java`

```java
// Trong ticket-infrastructure — Spring Data JPA auto-generate SQL
public interface TicketDetailJPAMapper extends JpaRepository<TicketDetail, Long> {
    Optional<TicketDetail> findById(Long id); // thừa, JpaRepository đã có sẵn
}
```

Spring Data JPA đọc tên method `findById` → tự generate SQL `SELECT * FROM ticket_item WHERE id = ?`. Không cần viết SQL thủ công.

---

## 7. Structure folder và DI flow trong project

### 7.1 Toàn bộ folder structure

Ký hiệu: `[I]` = interface &nbsp; `[C]` = class &nbsp; `[E]` = entity

```
booking_ticket/
│
├── ticket-start/                      ← Entry point, khởi động Spring Boot
│   └── src/main/java/org/ticket/
│       ├── Main.java
│       └── StartApplication.java
│
├── ticket-controller/                 ← Nhận HTTP request, trả HTTP response
│   └── src/main/java/org/ticket/controller/
│       ├── http/
│       │   ├── HiController.java                      [C]
│       │   └── TicketDetailController.java            [C]
│       └── model/
│           ├── enums/
│           │   ├── ResultCode.java                    [C] enum mã lỗi
│           │   └── ResultUtil.java                    [C] helper build response
│           └── vo/
│               └── ResultMessage.java                 [C] VO trả về client
│
├── ticket-application/                ← Điều phối luồng xử lý (orchestration)
│   └── src/main/java/org/ticket/application/
│       └── service/
│           ├── event/
│           │   ├── EventAppService.java               [I]
│           │   └── impl/
│           │       └── EventAppServiceImpl.java       [C] impl của EventAppService
│           └── ticket/
│               ├── TicketDetailAppService.java        [I] contract cho controller gọi
│               ├── cache/
│               │   └── TicketDetailCacheService.java  [C] xử lý cache + distributed lock
│               └── impl/
│                   └── TicketDetailAppServiceImpl.java [C] impl của TicketDetailAppService
│
├── ticket-domain/                     ← Business logic thuần, không biết kỹ thuật
│   └── src/main/java/org/ticket/domain/
│       ├── model/
│       │   └── entity/
│       │       ├── Ticket.java                        [E]
│       │       └── TicketDetail.java                  [E]
│       ├── repository/
│       │   ├── HiDomainRepository.java                [I] contract — infra sẽ implement
│       │   └── TicketDetailRepository.java            [I] contract — infra sẽ implement
│       └── service/
│           ├── HiDomainService.java                   [I]
│           ├── TicketDetailDomainService.java         [I] contract cho application gọi
│           └── Impl/
│               ├── HiDomainServiceImpl.java           [C] impl của HiDomainService
│               └── TicketDetailDomainServiceImpl.java [C] impl của TicketDetailDomainService
│
└── ticket-infrastructure/             ← Kỹ thuật: DB, Redis, Redisson
    └── src/main/java/org/ticket/infrastructure/
        ├── cache/
        │   └── redis/
        │       ├── RedisInfraService.java             [I] contract thao tác Redis
        │       └── RedisInfraServiceImpl.java         [C] impl của RedisInfraService
        ├── config/
        │   ├── AppConfig.java
        │   └── RedisConfig.java
        ├── distributed/
        │   └── redisson/
        │       ├── RedisDistributedLocker.java        [I] contract thao tác 1 lock
        │       ├── RedisDistributedService.java       [I] contract lấy lock theo key
        │       ├── config/
        │       │   └── RedissonConfig.java
        │       └── impl/
        │           └── RedisDistributedLockerImpl.java [C] impl của RedisDistributedLocker
        │                                                    và RedisDistributedService
        └── persistence/
            ├── mapper/
            │   └── TicketDetailJPAMapper.java         [I] extends JpaRepository — Spring tự gen SQL
            └── repository/
                ├── HiInfraRepositoryImpl.java         [C]
                └── TicketDetailInfraRepositoryImpl.java [C] impl của TicketDetailRepository
```

---

### 7.2 DI flow trong 1 request `GET /ticket/1/detail/2`

Sơ đồ dưới đây cho thấy **class nào DI class/interface nào** và **Spring inject implementation nào vào lúc runtime**.

```
HTTP Request: GET /ticket/1/detail/2
        │
        ▼
┌─────────────────────────────────────────────────────────────────┐
│ [ticket-controller]                                             │
│                                                                 │
│  TicketDetailController                                         │
│    field: TicketDetailAppService ticketDetailAppService         │
│           (interface — định nghĩa tại ticket-application)       │
│           Spring inject: TicketDetailAppServiceImpl ──────────┐ │
└──────────────────────────────────────────────────────────────┼─┘
                                                               │
                         Spring inject TicketDetailAppServiceImpl
                                                               │
        ┌──────────────────────────────────────────────────────▼─┐
        │ [ticket-application]                                    │
        │                                                         │
        │  TicketDetailAppServiceImpl                             │
        │    field: TicketDetailDomainService                     │
        │           (interface — định nghĩa tại ticket-domain)    │
        │           Spring inject: TicketDetailDomainServiceImpl  │
        │    field: TicketDetailCacheService                      │
        │           (class — cùng module ticket-application)      │
        │           Spring inject: TicketDetailCacheService ────┐ │
        └──────────────────────────────────────────────────────┼─┘
                                                               │
        ┌──────────────────────────────────────────────────────▼─┐
        │ [ticket-application]                                    │
        │                                                         │
        │  TicketDetailCacheService                               │
        │    field: TicketDetailDomainService                     │
        │           Spring inject: TicketDetailDomainServiceImpl ─┼──┐
        │    field: RedisInfraService                             │  │
        │           Spring inject: RedisInfraServiceImpl ─────────┼──┼──┐
        │    field: RedisDistributedService                       │  │  │
        │           Spring inject: RedisDistributedLockerImpl ────┼──┼──┼──┐
        └────────────────────────────────────────────────────────┘  │  │  │
                                                                     │  │  │
        ┌────────────────────────────────────────────────────────────▼──┘  │
        │ [ticket-domain]                                         │         │
        │                                                         │         │
        │  TicketDetailDomainServiceImpl                          │         │
        │    field: TicketDetailRepository                        │         │
        │           (interface — định nghĩa tại ticket-domain)    │         │
        │           Spring inject: TicketDetailInfraRepositoryImpl┼──┐      │
        └────────────────────────────────────────────────────────┘  │      │
                                                                     │      │
        ┌────────────────────────────────────────────────────────────▼──────▼──┐
        │ [ticket-infrastructure]                                               │
        │                                                                       │
        │  TicketDetailInfraRepositoryImpl  (implements TicketDetailRepository) │
        │    field: TicketDetailJPAMapper                                       │
        │           Spring inject: JPA proxy (auto-generate bởi Spring Data)   │
        │                                    │                                  │
        │  RedisInfraServiceImpl  (implements RedisInfraService)                │
        │  RedisDistributedLockerImpl  (implements RedisDistributedLocker)      │
        └───────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
                          MySQL / Redis (external)
```

---

### 7.3 Tóm tắt DI — ai inject ai

| Class | DI field | Kiểu | Interface định nghĩa tại | Spring inject |
|---|---|---|---|---|
| `TicketDetailController` | `ticketDetailAppService` | interface | `ticket-application` | `TicketDetailAppServiceImpl` |
| `TicketDetailAppServiceImpl` | `ticketDetailDomainService` | interface | `ticket-domain` | `TicketDetailDomainServiceImpl` |
| `TicketDetailAppServiceImpl` | `ticketDetailCacheService` | class | `ticket-application` | `TicketDetailCacheService` |
| `TicketDetailCacheService` | `ticketDetailDomainService` | interface | `ticket-domain` | `TicketDetailDomainServiceImpl` |
| `TicketDetailCacheService` | `redisInfraService` | interface | `ticket-infrastructure` | `RedisInfraServiceImpl` |
| `TicketDetailCacheService` | `redisDistributedService` | interface | `ticket-infrastructure` | `RedisDistributedLockerImpl` |
| `TicketDetailDomainServiceImpl` | `ticketDetailRepository` | interface | `ticket-domain` | `TicketDetailInfraRepositoryImpl` |
| `TicketDetailInfraRepositoryImpl` | `ticketDetailJPAMapper` | interface (JPA) | `ticket-infrastructure` | JPA proxy (Spring Data) |

---

## 8. "Phụ thuộc" nghĩa là gì?

### Định nghĩa

Trong class có **DI một class/interface khác** = class đó **phụ thuộc** vào thứ được inject.

```java
public class A {
    private final B b; // A phụ thuộc B — B không có thì A không chạy được
}
```

**Vấn đề không phải ở chỗ có phụ thuộc hay không** — phụ thuộc là điều không thể tránh. Vấn đề là **phụ thuộc vào cái gì, và cái đó được định nghĩa ở module nào.**

---

### Quy tắc tổng quát cho mọi module

> **Một module chỉ được phép DI (class hoặc interface) từ những module mà dependency direction cho phép.**

Nhìn lại dependency direction của project (mục 4):

```
ticket-domain           ← không phụ thuộc ai
      ▲
ticket-infrastructure  ← phụ thuộc: ticket-domain
      ▲
ticket-application     ← phụ thuộc: ticket-domain + ticket-infrastructure
      ▲
ticket-controller      ← phụ thuộc: ticket-application (+ ticket-domain cho VO/Entity)
```

Quy tắc đọc từ sơ đồ này:

| Module | Được phép DI từ | Không được DI từ |
|---|---|---|
| `ticket-controller` | `ticket-application`, `ticket-domain` | `ticket-infrastructure` |
| `ticket-application` | `ticket-domain`, `ticket-infrastructure` | — |
| `ticket-infrastructure` | `ticket-domain` | `ticket-application`, `ticket-controller` |
| `ticket-domain` | **không ai** | tất cả |

---

### Tại sao `ticket-controller` không được DI từ `ticket-infrastructure`?

Dù mũi tên không cấm trực tiếp, nhưng nếu `ticket-controller` DI thẳng vào infra:

```java
// [ticket-controller] TicketDetailController.java
import org.ticket.infrastructure.persistence.mapper.TicketDetailJPAMapper; // ❌

@RestController
public class TicketDetailController {
    private final TicketDetailJPAMapper jpaMapper; // controller gọi DB trực tiếp — bỏ qua application layer

    @GetMapping("/{id}")
    public TicketDetail get(@PathVariable Long id) {
        return jpaMapper.findById(id).orElse(null); // không qua service, không có cache, không có validation
    }
}
```

Controller bỏ qua toàn bộ business logic ở application và domain — không có cache, không có validation, không có orchestration. Đây là **logic leak** — kỹ thuật (JPA) lọt lên tầng HTTP.

---

### Domain là trường hợp đặc biệt nhất — Dependency Inversion

Domain không chỉ "không được DI từ module ngoài" mà còn làm điều ngược lại: **domain định nghĩa interface, rồi bắt infrastructure implement theo**.

Đây gọi là **Dependency Inversion Principle (DIP)** — tầng cao định nghĩa contract, tầng thấp phải tuân theo.

**Sai — domain DI thứ gì đó từ ngoài (dù là interface):**

```java
// [ticket-domain] TicketDetailDomainServiceImpl.java
import org.ticket.infrastructure.cache.redis.RedisInfraService; // ❌ interface, nhưng từ infra!

public class TicketDetailDomainServiceImpl implements TicketDetailDomainService {
    private final RedisInfraService redisInfraService; // domain phải import từ infra → vi phạm
}
```

Là interface hay class không quan trọng — quan trọng là **nó nằm ở module nào**. Domain phải `import` từ `ticket-infrastructure` → domain biết infra tồn tại → vi phạm.

**Đúng — domain tự định nghĩa interface, infra implement:**

```java
// [ticket-domain] TicketDetailRepository.java  ← domain tự viết interface này
package org.ticket.domain.repository;

public interface TicketDetailRepository {
    Optional<TicketDetail> findById(Long id);
}
```

```java
// [ticket-domain] TicketDetailDomainServiceImpl.java
import org.ticket.domain.repository.TicketDetailRepository; // ✅ import trong chính ticket-domain

public class TicketDetailDomainServiceImpl implements TicketDetailDomainService {
    private final TicketDetailRepository ticketDetailRepository; // ✅ interface do domain định nghĩa

    public TicketDetail getTicketDetailById(Long ticketId) {
        return ticketDetailRepository.findById(ticketId).orElse(null);
    }
}
```

```java
// [ticket-infrastructure] TicketDetailInfraRepositoryImpl.java
// Infrastructure "cúi đầu" implement contract của domain
public class TicketDetailInfraRepositoryImpl implements TicketDetailRepository { // ✅ implement interface từ domain

    private final TicketDetailJPAMapper ticketDetailJPAMapper; // JPA nằm đúng chỗ — trong infra

    public Optional<TicketDetail> findById(Long id) {
        return ticketDetailJPAMapper.findById(id);
    }
}
```

Domain nói "tao cần ai đó có thể `findById`". Infrastructure đọc contract đó và implement. Spring inject `TicketDetailInfraRepositoryImpl` vào lúc runtime — domain không biết, không cần biết ai đang implement.

```
ticket-domain định nghĩa:   TicketDetailRepository (interface)
                                        ↑ implements
ticket-infrastructure:      TicketDetailInfraRepositoryImpl (biết JPA, biết MySQL)
```

---

### Dependency map thực tế của project

```
[ticket-controller]
  TicketDetailController
    └─ DI: TicketDetailAppService         (interface — ticket-application) ✅ đúng chiều
                                           (KHÔNG DI thẳng vào infra hay domain service)

[ticket-application]
  TicketDetailAppServiceImpl
    └─ DI: TicketDetailDomainService      (interface — ticket-domain) ✅
    └─ DI: TicketDetailCacheService       (class     — ticket-application, cùng module) ✅

  TicketDetailCacheService
    └─ DI: TicketDetailDomainService      (interface — ticket-domain) ✅
    └─ DI: RedisInfraService              (interface — ticket-infrastructure) ✅ application được phép dùng infra
    └─ DI: RedisDistributedService        (interface — ticket-infrastructure) ✅ application được phép dùng infra
                                           (⚠️ nhưng đây là vùng xám về vị trí đặt class — xem mục 9)

[ticket-domain]
  TicketDetailDomainServiceImpl
    └─ DI: TicketDetailRepository         (interface — ticket-domain, do chính domain định nghĩa) ✅
                                           (KHÔNG DI bất kỳ thứ gì từ module ngoài)

[ticket-infrastructure]
  TicketDetailInfraRepositoryImpl         implements TicketDetailRepository (interface từ ticket-domain) ✅
    └─ DI: TicketDetailJPAMapper          (JpaRepository — ticket-infrastructure) ✅ kỹ thuật nằm đúng tầng
```

**Nhận xét từng module:**

- **`ticket-controller`** — chỉ biết application interface, không nhìn thấy infra hay domain service trực tiếp → đúng vai trò HTTP adapter
- **`ticket-application`** — được phép DI cả domain lẫn infra vì nhiệm vụ là **orchestration** (điều phối cache + DB + business logic)
- **`ticket-domain`** — không DI từ bất kỳ module nào, còn chủ động định nghĩa interface để infra implement → **core cứng nhất, ổn định nhất**
- **`ticket-infrastructure`** — DI class JPA cụ thể là hợp lệ vì đây chính là tầng kỹ thuật, nhiệm vụ là biết cách nói chuyện với DB/Redis

---

### ⚠️ Quy tắc quan trọng nhất của chương này

> **Giao tiếp qua ranh giới module → bắt buộc dùng interface.**
> **Giao tiếp trong nội bộ module → không bắt buộc.**

**Qua ranh giới module** — bắt buộc interface, vì:
- Module gọi không được biết implementation cụ thể của module kia
- Sau này muốn thay implementation (đổi DB, đổi cache...) chỉ cần đổi 1 chỗ, không ảnh hưởng bên gọi

```
ticket-controller  ──gọi qua──►  TicketDetailAppService     [I] (định nghĩa ở ticket-application)
ticket-application ──gọi qua──►  TicketDetailDomainService  [I] (định nghĩa ở ticket-domain)
ticket-application ──gọi qua──►  RedisInfraService          [I] (định nghĩa ở ticket-infrastructure)
ticket-domain      ──gọi qua──►  TicketDetailRepository     [I] (định nghĩa ở ticket-domain)
```

**Trong nội bộ module** — không bắt buộc interface, vì:
- Cùng module thì biết nhau là chuyện bình thường
- Thêm interface vào chỉ tạo ra boilerplate thừa, không có lợi ích gì

```java
// [ticket-application] TicketDetailAppServiceImpl.java
// TicketDetailCacheService là class, không phải interface — OK vì cùng module
private final TicketDetailCacheService ticketDetailCacheService; // ✅
```

---

## 9. Tại sao Domain không được biết Infrastructure?

Đây là câu hỏi quan trọng nhất. Hãy xem điều gì xảy ra nếu **Domain biết JPA:**

```java
// SÁCH GIÁO KHOA: ĐỪNG LÀM THẾ NÀY
public class TicketDetailDomainServiceImpl {

    @Autowired
    private TicketDetailJPAMapper jpaMapper; // phụ thuộc JPA cụ thể

    public TicketDetail getTicketDetailById(Long id) {
        return jpaMapper.findById(id).orElse(null); // gọi JPA trực tiếp
    }
}
```

**Hệ quả:**

1. **Muốn test logic nghiệp vụ** → buộc phải có DB chạy → test chậm, phức tạp
2. **Muốn đổi MySQL → MongoDB** → phải sửa file trong domain — business logic bị kéo vào thay đổi vì lý do kỹ thuật
3. **Muốn tái sử dụng logic** ở Kafka consumer, batch job... → không thể vì logic đang bám vào JPA

**Giải pháp đúng (như project này đang làm):**

```java
// Domain chỉ biết interface
public class TicketDetailDomainServiceImpl {

    private final TicketDetailRepository ticketDetailRepository; // interface!

    public TicketDetail getTicketDetailById(Long id) {
        return ticketDetailRepository.findById(id).orElse(null);
    }
}
```

Test business logic không cần DB:
```java
@Test
void shouldReturnNullWhenTicketNotFound() {
    TicketDetailRepository fakeRepo = mock(TicketDetailRepository.class);
    when(fakeRepo.findById(99L)).thenReturn(Optional.empty());

    TicketDetailDomainService service = new TicketDetailDomainServiceImpl(fakeRepo);
    assertNull(service.getTicketDetailById(99L));
    // Không cần @SpringBootTest, không cần DB, không cần Redis
}
```

---

## 10. TicketDetailCacheService — vùng xám của kiến trúc

`TicketDetailCacheService` hiện nằm ở `ticket-application` nhưng import trực tiếp từ `ticket-infrastructure`:

```java
// ticket-application/service/ticket/cache/TicketDetailCacheService.java
import org.ticket.infrastructure.cache.redis.RedisInfraService;       // từ infra
import org.ticket.infrastructure.distributed.redisson.RedisDistributedLocker; // từ infra
import org.ticket.infrastructure.distributed.redisson.RedisDistributedService; // từ infra
```

### Tại sao đây là vùng xám?

**Quan điểm 1 — OK vì Application được phép dùng Infrastructure:**

Theo dependency direction của project: `ticket-application depends on ticket-infrastructure`. Application layer được thiết kế để gọi cả domain lẫn infra. `TicketDetailCacheService` đang điều phối "khi nào lấy từ cache, khi nào lấy từ DB" — đây là **orchestration logic**, thuộc application.

**Quan điểm 2 — Nên chuyển vào Infrastructure:**

Cache là kỹ thuật (technical concern), không phải nghiệp vụ. `TicketDetailInfraRepositoryImpl` có thể tự implement cache bên trong mà Application không cần biết:

```java
// Phương án thay thế: cache ẩn trong infra
@Service
public class TicketDetailInfraRepositoryImpl implements TicketDetailRepository {

    private final TicketDetailJPAMapper jpaMapper;
    private final RedisInfraService redisInfraService;

    @Override
    public Optional<TicketDetail> findById(Long id) {
        // cache logic ở đây — application không biết cache tồn tại
        TicketDetail cached = redisInfraService.getObject(key(id), TicketDetail.class);
        if (cached != null) return Optional.of(cached);

        Optional<TicketDetail> result = jpaMapper.findById(id);
        result.ifPresent(t -> redisInfraService.setObject(key(id), t));
        return result;
    }
}
```

**Kết luận:** Cả hai đều là lựa chọn hợp lệ. Project hiện tại đặt cache ở Application layer để **Application kiểm soát rõ strategy** (VIP vs Normal cache). Đây là trade-off có chủ đích, không phải lỗi kiến trúc.

---

## 11. Các thành phần cốt lõi của DDD

### Entity

Đối tượng có **identity (ID)** và **lifecycle** (trạng thái thay đổi theo thời gian). Business logic nằm **bên trong** entity, không nằm ngoài service.

```java
// Hiện tại project chỉ có data fields, chưa có business logic bên trong
@Entity
@Table(name = "ticket_item")
public class TicketDetail {
    @Id
    private Long id;
    private String name;
    private int stockInitial;
    private int stockAvailable;
    private boolean isStockPrepared;
    private Long priceFlash;
    private Date saleStartTime;
    private Date saleEndTime;
    // ...

    // TODO: business logic nên nằm ở đây, ví dụ:
    // public boolean isOnSale() {
    //     Date now = new Date();
    //     return now.after(saleStartTime) && now.before(saleEndTime);
    // }
    //
    // public boolean hasStock() {
    //     return stockAvailable > 0;
    // }
}
```

---

### Aggregate Root

Nhóm các Entity liên quan, chỉ có **1 điểm vào duy nhất** để thay đổi trạng thái. Ví dụ trong project này, `Ticket` là Aggregate Root chứa danh sách `TicketDetail`.

```java
// Chưa implement trong project — đây là hướng tới
public class Ticket { // Aggregate Root
    private Long id;
    private List<TicketDetail> items; // TicketDetail thuộc về Ticket aggregate

    // Thay đổi item phải đi qua Ticket, không được sửa TicketDetail trực tiếp từ ngoài
    public TicketDetail reserveItem(Long itemId, int quantity) {
        TicketDetail item = findItem(itemId);
        if (!item.hasStock()) throw new DomainException("Hết vé");
        if (!item.isOnSale()) throw new DomainException("Ngoài giờ flash sale");
        item.decreaseStock(quantity);
        return item;
    }
}
```

---

### Value Object

Đối tượng **bất biến**, không có identity, so sánh theo **giá trị**. Dùng để thay thế primitive types cho rõ nghĩa.

```java
// Chưa có trong project — nên thêm
public record Money(Long amount, String currency) {
    public boolean isCheaperThan(Money other) {
        return this.amount < other.amount;
    }
}

// Thay vì dùng Long priceFlash, Long priceOriginal
// → dùng Money priceFlash, Money priceOriginal
// Lợi ích: không thể nhầm lẫn currency, có validation, có semantic rõ ràng
```

---

### Domain Event

Phát ra khi điều gì đó quan trọng xảy ra. Application layer lắng nghe và xử lý side effects.

```java
// Chưa có trong project — nên thêm khi implement booking
public class TicketReservedEvent {
    private final Long ticketDetailId;
    private final Long userId;
    private final int quantity;
    private final LocalDateTime occurredAt;
}

// Application lắng nghe → gửi email, push notification, ghi analytics
@EventListener
public void onTicketReserved(TicketReservedEvent event) {
    emailService.sendConfirmation(event.getUserId());
}
```

---

## 12. Trạng thái hiện tại và còn thiếu gì

### Đã có và đúng kiến trúc ✅

| Thành phần | File | Nhận xét |
|---|---|---|
| Entity | `TicketDetail.java`, `Ticket.java` | Có @Entity, @Table — đúng |
| Repository interface | `TicketDetailRepository.java` | Đúng chỗ, đúng vai trò |
| Domain Service interface + impl | `TicketDetailDomainService` | Chỉ dùng domain interface |
| Infrastructure implement | `TicketDetailInfraRepositoryImpl` | Implement domain interface đúng |
| JPA Mapper | `TicketDetailJPAMapper` | Spring Data JPA auto Bean |
| AppService interface + impl | `TicketDetailAppService` | Điều phối đúng |
| Cache orchestration | `TicketDetailCacheService` | Xử lý cache stampede + penetration |
| Controller | `TicketDetailController` | Chỉ gọi AppService interface |

### Còn thiếu (cần khi implement feature thật)

| Thành phần | Trạng thái | Cần làm gì |
|---|---|---|
| Business logic trong Entity | Chưa có | Thêm `isOnSale()`, `hasStock()`, `decreaseStock()` vào `TicketDetail` |
| Aggregate Root | Chưa có | `Ticket` chứa `List<TicketDetail>`, có method `reserveItem()` |
| Value Object | Chưa có | `Money`, `TicketId`... thay thế primitive |
| Domain Event | Chưa có | `TicketReservedEvent` khi đặt vé thành công |
| Exception domain | Chưa có | `DomainException` với message nghiệp vụ rõ ràng |

### Cấu trúc domain nên hướng tới

```
ticket-domain/
  ticket/
    entity/
      Ticket.java              ← Aggregate Root
      TicketDetail.java        ← Entity (đã có, cần thêm business logic)
    valueobject/
      TicketId.java
      Money.java
    repository/
      TicketDetailRepository.java  ← đã có
    service/
      TicketDetailDomainService.java   ← đã có
      impl/
        TicketDetailDomainServiceImpl.java ← đã có
    event/
      TicketReservedEvent.java     ← chưa có
  shared/
    exception/
      DomainException.java         ← chưa có
```

---

> **Tóm lại một câu:** DDD tách biệt "luật chơi nghiệp vụ" (Domain) khỏi "cách thực thi kỹ thuật" (Infrastructure). Domain định nghĩa **interface** — infra **implement**. Controller chỉ biết AppService. AppService điều phối domain + infra. Đổi công nghệ chỉ sửa infra, không đụng domain.
