# Cache & Distributed Lock

## 1. Vấn đề

### Race Condition khi nhiều server chạy song song

Hệ thống booking ticket thường scale ngang (nhiều server). Khi 1000 người cùng bấm mua vé cuối cùng:

```
Thread A (Server 1): đọc DB → còn 1 vé  ┐
Thread B (Server 2): đọc DB → còn 1 vé  ┘ cùng lúc

Thread A: trừ tồn kho → 0 vé, ghi DB ✅
Thread B: trừ tồn kho → 0 vé, ghi DB ✅  ← OVERSELL ❌
```

`synchronized` của Java không giải quyết được vì nó chỉ lock trong 1 JVM:

```
User A → Server 1 ──┐
User B → Server 2 ──┘  synchronized không đồng bộ giữa 2 JVM ❌
```

---

### Cache Stampede (Thundering Herd)

Cache expire đúng lúc có 1000 request đồng thời → tất cả miss cache → đồng loạt đánh vào DB:

```
Cache expire lúc T
    │
    ├── Request 1: miss → query DB ┐
    ├── Request 2: miss → query DB │ cùng lúc → DB nhận 1000 query → quá tải 💥
    ├── Request 3: miss → query DB │
    └── ...1000 requests...        ┘
```

---

### Cache Penetration

Request với ID không tồn tại trong DB (ví dụ `detailId=9999`) → DB trả null → code không cache null → mọi request đều bypass cache và hit DB mãi mãi:

```
GET /ticket/9999 (không tồn tại)
    │
    ├── Check Redis: miss
    ├── Query DB: null
    ├── Không set cache (vì null)
    └── Request tiếp theo: lặp lại từ đầu ← hacker có thể DDoS DB bằng cách này
```

---

## 2. Giải pháp

| Vấn đề | Giải pháp |
|---|---|
| Race Condition (multi-server) | Distributed Lock — Redis làm trọng tài trung lập cho cả cluster |
| Cache Stampede | Distributed Lock + Double-check cache — chỉ 1 request được query DB |
| Cache Penetration | Null-value caching — cache cả kết quả null |

---

## 3. Flow thực tế: GET /ticket/1/detail/1

Flow này áp dụng đồng thời cả 3 giải pháp trên:

```
GET /ticket/1/detail/1
        │
        ▼
[Controller] TicketDetailController
  → tách detailId từ path, gọi AppService
        │
        ▼
[Application] TicketDetailAppServiceImpl
  → ủy quyền cho CacheService (không chứa business logic)
        │
        ▼
[CacheService] TicketDetailCacheService
        │
        │  Bước 1: Check Redis (Cache-Aside)
        ├──► Redis.get("PRO_TICKET:ITEM:1")
        │         │
        │    Có data ──────────────────────────────────► Trả về ngay ✓
        │         │
        │    Cache miss
        │         │
        │  Bước 2: Lấy Distributed Lock
        │         └──► tryLock("PRO_LOCK_KEY_ITEM1", wait=1s, lease=5s)
        │                   │
        │    Không lấy được sau 1s ────────────────────► Trả null ✗
        │                   │
        │    Lấy được lock
        │         │
        │  Bước 3: Double-check Redis lần 2
        │         │
        │    Có data (request trước vừa set) ──────────► Trả về ✓
        │         │
        │    Vẫn miss → chỉ 1 request duy nhất vào được đây
        │         │
        │  Bước 4: Query DB
        │         ▼
        │   [Domain] → [Infrastructure] → [JPA] → MySQL
        │         │
        │         └──► trả TicketDetail (có thể null nếu ID không tồn tại)
        │
        │  Bước 5: Set Redis (kể cả khi null — chống Cache Penetration)
        │         └──► Redis.set("PRO_TICKET:ITEM:1", data)
        │
        │  Bước 6: Release Lock
        │
        ▼
[Controller] → wrap vào ResultMessage<TicketDetail> → trả JSON về client
```

---

## 4. Chi tiết từng kỹ thuật

---

### 4.1 Cache-Aside (Lazy Loading)

Pattern đọc dữ liệu cơ bản khi kết hợp Redis + DB. App tự chủ động đọc/ghi cache, DB và Redis không biết nhau:

```
Check Redis → Có? → Trả về ngay ✓
           → Không? → Query DB → Lưu Redis → Trả về
```

Đây là nền tảng, nhưng **tự nó chưa giải quyết được Cache Stampede và Cache Penetration** — cần thêm 4.2 và 4.3.

---

### 4.2 Distributed Lock

**Kiến trúc trong project:**

```
RedisDistributedService       → interface: "lấy lock theo key nào?"
        ↑ implements
RedisDistributedLockerImpl    → dùng Redisson lấy RLock từ Redis, trả về RedisDistributedLocker
        ↓ trả về
RedisDistributedLocker        → interface: "dùng lock này như thế nào?"
                                (implement dưới dạng anonymous class bên trong Impl)
```

| Interface/Class | Vai trò |
|---|---|
| `RedisDistributedService` | Cổng vào — nhận `lockKey`, trả về locker |
| `RedisDistributedLockerImpl` | Triển khai thực tế, wrap Redisson `RLock` |
| `RedisDistributedLocker` | Hợp đồng sử dụng lock: tryLock, lock, unlock, isLocked |

**Flow hoạt động:**

```
getDistributedLock("PRO_LOCK_KEY_ITEM1")
        ↓
redissonClient.getLock("PRO_LOCK_KEY_ITEM1") → trả về RLock

tryLock(waitTime=1s, leaseTime=5s)
        ↓
Redisson gửi lệnh tới Redis
        ├── Key chưa tồn tại → SET key → true  → vào critical section ✅
        └── Key đã tồn tại   → chờ tối đa 1s
                ├── Lock release trong 1s → lấy được → true ✅
                └── Hết 1s vẫn chưa được → false → trả null ❌

unlock()
        ↓
Kiểm tra isHeldByCurrentThread()
        ├── Đúng → DEL key Redis → thread khác có thể lấy lock
        └── Sai  → bỏ qua (tránh unlock nhầm lock của thread khác)
```

**Tại sao phải `isHeldByCurrentThread()` trước khi unlock?**

Nếu chỉ check `isLocked()`:

```
leaseTime = 5s

Thread A: lấy lock ✅ → xử lý chậm, quá 5s → lock tự expire
Thread B: lấy lock ✅ (A đã expire)
Thread A: xử lý xong → gọi unlock()
          → isLocked() = true (B đang giữ!)
          → DEL key Redis ← A vừa unlock nhầm lock của B ❌
Thread C: lấy lock ✅ → B và C cùng vào critical section → race condition 💥
```

Redisson lưu trong Redis không chỉ "lock đang bị giữ" mà còn **ai đang giữ** — dưới dạng Redis Hash:

```
Redis key: "PRO_LOCK_KEY_ITEM1"
Redis value (hash):
    "<clientId:threadId>" → 1    ← số lần reentrant (thường = 1)
    TTL: 5s
```

`isHeldByCurrentThread()` check xem `<clientId:threadId>` của thread hiện tại có trong hash không. Nếu lock đã expire hoặc thread khác giữ → `false` → bỏ qua.

| Tình huống | `isLocked()` | `isHeldByCurrentThread()` | Kết quả |
|---|---|---|---|
| Thread A giữ lock, A gọi unlock | true | true | unlock đúng ✅ |
| Lock đã expire, A gọi unlock muộn | false | false | bỏ qua ✅ |
| Thread B đang giữ, A gọi unlock nhầm | true | false | bỏ qua ✅ |

**Tham số `waitTime` và `leaseTime`:**

| Tham số | Ý nghĩa |
|---|---|
| `waitTime` | Chờ tối đa bao lâu để lấy lock — hết thời gian → trả false |
| `leaseTime` | Lock tự động expire sau bao lâu — phòng server crash mà không kịp unlock |

```
Server 1: lấy lock ✅ → đang xử lý → CRASH 💥 → không bao giờ gọi unlock()

Không có leaseTime: lock tồn tại mãi trên Redis → toàn bộ cluster bị treo ❌
Có leaseTime=5s:    Redis tự xoá key sau 5s     → các server khác tiếp tục được ✅
```

**Tại sao dùng Redisson thay vì tự implement?**

Tự implement lock bằng Redis `SET NX EX` dễ sai ở các edge case:

| Edge case | Tự implement | Redisson |
|---|---|---|
| Server crash trước khi unlock | Lock bị treo mãi ❌ | `leaseTime` tự release ✅ |
| Thread A unlock nhầm của Thread B | Xảy ra nếu không check owner ❌ | `isHeldByCurrentThread()` ✅ |
| Process chạy lâu hơn `leaseTime` | Lock expire giữa chừng ❌ | Watchdog tự gia hạn ✅ |
| Redis cluster failover | Phức tạp để xử lý ❌ | Hỗ trợ RedLock algorithm ✅ |

> **Watchdog:** Nếu không set `leaseTime` (hoặc = -1), Redisson tự gia hạn lock mỗi 10s miễn là process còn sống.

**Tại sao tách thành 2 interface?**

```
RedisDistributedService   → "lấy lock theo key nào?"     (factory)
RedisDistributedLocker    → "lock này dùng như thế nào?" (product)
```

3 lý do:

**1 — Separation of Concerns:** 2 interface trả lời 2 câu hỏi khác nhau. Gộp 1 thì caller vừa phải biết cách lấy lock vừa biết cách dùng lock — logic lẫn lộn.

**2 — Mỗi lockKey cần 1 instance riêng:**
```java
RedisDistributedLocker lock1 = service.getDistributedLock("ticket:buy:1");
RedisDistributedLocker lock2 = service.getDistributedLock("ticket:buy:2");
// lock1 và lock2 độc lập, bind với 2 RLock khác nhau trên Redis
```

**3 — Open/Closed Principle:** Application layer chỉ biết đến interface. Khi cần đổi Redisson sang ZooKeeper, thêm class mới implement interface — không sửa 1 dòng nào ở tầng trên.

---

### 4.3 Double-check Cache

Bước check Redis lần 2 **sau khi đã lấy được lock** — thường bị bỏ sót khi implement.

3 request A, B, C cùng miss cache cùng lúc:

```
A, B, C đều miss cache → cùng tranh lock
        │
        ├── A lấy lock → query DB → set Redis → release lock
        │
        ├── B lấy lock (A vừa release)
        │       │
        │   Không double-check → B query DB (lãng phí, data đã có) ❌
        │   Có double-check    → B check Redis → đã có data → trả về ✓
        │
        └── C tương tự B
```

Lock đảm bảo từng thời điểm chỉ 1 thread chạy, nhưng B và C vẫn **xếp hàng chờ**. Double-check giúp chúng không đánh DB dư thừa sau khi A đã set cache xong.

---

### 4.4 Null-value Caching

Set cache kể cả khi DB trả về null — ngăn Cache Penetration:

```java
// Dù ticketDetail = null, vẫn set vào Redis
redisInfraService.setObject(genEventItemKey(id), ticketDetail);
// Lần sau cùng ID → Redis trả null → không hỏi DB nữa ✓
```

Lưu ý: null trong Redis cần có TTL hợp lý (không set mãi mãi) để khi DB có data thật thì cache tự làm mới.

---

### 4.5 Ví dụ thực tế: Mua vé

Kết hợp toàn bộ 4 kỹ thuật trong 1 flow: Cache-Aside + Distributed Lock + Double-check Cache + Null-value Caching.

```java
@Service
@RequiredArgsConstructor
public class TicketPurchaseService {

    private final RedisDistributedService distributedService;
    private final RedisInfraService redisInfraService;
    private final TicketRepository ticketRepository;

    private String stockCacheKey(Long ticketId) {
        return "TICKET:STOCK:" + ticketId;
    }

    public void purchaseTicket(Long ticketId, Long userId) {

        // ── Bước 1: Cache-Aside — đọc tồn kho từ Redis trước khi hỏi DB ──────────
        // Happy path: cache hit → không cần lock, không cần vào DB
        Integer stock = redisInfraService.getObject(stockCacheKey(ticketId), Integer.class);
        if (stock != null && stock <= 0) {
            // Cache hit, tồn kho = 0 → từ chối luôn, không cần vào lock
            throw new RuntimeException("Vé đã hết");
        }

        // ── Bước 2: Distributed Lock — tranh lock theo từng ticketId ─────────────
        // Lock key riêng theo ticketId, tránh bottleneck nếu dùng 1 lock chung
        // waitTime=3s: chờ tối đa 3s để lấy lock
        // leaseTime=10s: tự release nếu server crash, không kịp gọi unlock()
        RedisDistributedLocker lock = distributedService.getDistributedLock("TICKET:BUY:LOCK:" + ticketId);
        try {
            boolean acquired = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!acquired) {
                // Hết 3s vẫn không lấy được lock → hệ thống đang có nhiều request tranh nhau
                throw new RuntimeException("Hệ thống đang bận, vui lòng thử lại sau");
            }

            // ── Bước 3: Double-check Cache — check lại sau khi đã vào lock ────────
            // Request trước có thể đã set cache trong khi mình đang chờ lock
            // Nếu không double-check: vẫn query DB dù data đã có trong cache → lãng phí
            stock = redisInfraService.getObject(stockCacheKey(ticketId), Integer.class);
            if (stock != null && stock <= 0) {
                throw new RuntimeException("Vé đã hết");
            }

            // ── Bước 4: Query DB — chỉ 1 thread trên toàn cluster vào được đây ───
            Ticket ticket = ticketRepository.findById(ticketId)
                    .orElseThrow(() -> new RuntimeException("Ticket không tồn tại"));

            // ── Bước 5: Null-value Caching — set cache kể cả khi tồn kho = 0 ──────
            // Nếu chỉ cache khi stock > 0: request với vé hết vẫn bypass cache mãi mãi
            // → set cache ngay sau khi đọc DB, trước khi kiểm tra business logic
            redisInfraService.setObject(stockCacheKey(ticketId), ticket.getStock());

            if (ticket.getStock() <= 0) {
                throw new RuntimeException("Vé đã hết");
            }

            // ── Bước 6: Xử lý business — trừ tồn kho, ghi DB ────────────────────
            ticket.setStock(ticket.getStock() - 1);
            ticketRepository.save(ticket);

            // ── Bước 7: Cập nhật cache sau khi ghi DB ────────────────────────────
            // Đồng bộ lại cache với giá trị mới nhất vừa ghi xuống DB
            redisInfraService.setObject(stockCacheKey(ticketId), ticket.getStock());

        } catch (InterruptedException e) {
            // Thread bị ngắt trong khi chờ lock — restore interrupt flag để caller biết
            Thread.currentThread().interrupt();
            throw new RuntimeException("Bị ngắt khi chờ lock");
        } finally {
            // Luôn đặt trong finally — đảm bảo release lock dù có exception ở bất kỳ bước nào
            lock.unlock();
        }
    }
}
```
