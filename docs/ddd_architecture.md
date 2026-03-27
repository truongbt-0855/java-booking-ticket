# DDD Architecture — Booking Ticket Project

## Mục lục
1. [DDD là gì?](#1-ddd-là-gì)
2. [So sánh DDD vs MVC](#2-so-sánh-ddd-vs-mvc)
3. [Các layer trong project](#3-các-layer-trong-project)
4. [Mối quan hệ và dependency giữa các layer](#4-mối-quan-hệ-và-dependency-giữa-các-layer)
5. [Các thành phần cốt lõi của DDD](#5-các-thành-phần-cốt-lõi-của-ddd)
6. [Flow thực tế: Đặt vé concert](#6-flow-thực-tế-đặt-vé-concert)
7. [Ưu điểm, nhược điểm](#7-ưu-điểm-nhược-điểm)
8. [Trạng thái hiện tại của project](#8-trạng-thái-hiện-tại-của-project)

---

## 1. DDD là gì?

**Domain-Driven Design (DDD)** là một cách tổ chức code xoay quanh **nghiệp vụ (domain)** thay vì xoay quanh công nghệ.

Ý tưởng cốt lõi:
- Code phản ánh đúng ngôn ngữ nghiệp vụ (đặt vé, huỷ vé, thanh toán...)
- Business logic tập trung trong Domain, không bị pha trộn với DB hay HTTP
- Các layer phụ thuộc **vào Domain**, Domain không phụ thuộc vào ai

---

## 2. So sánh DDD vs MVC

### MVC truyền thống

```
Controller → Service → Repository (JPA) → Database
```

Vấn đề điển hình trong MVC:

```java
@Service
public class TicketService {

    @Autowired
    private JpaTicketRepository repo; // biết cụ thể là JPA

    public void bookTicket(Long eventId, Long userId, int quantity) {
        // validate business rule
        // gọi DB trực tiếp
        // gửi email
        // tất cả nhồi vào 1 chỗ → God Service
    }
}
```

Hệ quả:
- Business logic lẫn lộn với infra (JPA, email, Kafka...)
- Muốn đổi MySQL → MongoDB phải sửa cả Service
- Test Service buộc phải mock DB
- Team lớn → conflict liên tục vì mọi thứ gộp vào Service

---

### DDD

```
Controller → AppService → DomainService → Repository (interface)
                                                ↑
                                         InfraRepositoryImpl (DB thật)
```

Domain chỉ làm việc với **interface**, không biết impl là gì.

```java
// Domain chỉ biết interface này
public interface TicketRepository {
    void save(Ticket ticket);
    Optional<Ticket> findById(TicketId id);
}

// Infra implement — domain không biết file này tồn tại
@Repository
public class JpaTicketRepositoryImpl implements TicketRepository {
    @Autowired JpaTicketJpaRepo jpaRepo;

    @Override
    public void save(Ticket ticket) {
        jpaRepo.save(toEntity(ticket));
    }
}
```

---

## 3. Các layer trong project

Project gồm 5 Maven module, mỗi module là 1 layer:

```
booking_ticket/
├── ticket-domain           # Nghiệp vụ thuần tuý
├── ticket-application      # Điều phối luồng xử lý
├── ticket-infrastructure   # Kỹ thuật: DB, Redis, Kafka...
├── ticket-controller       # HTTP endpoint
└── ticket-start            # Điểm khởi động Spring Boot
```

### ticket-domain

**Vai trò:** Trái tim của hệ thống. Chứa toàn bộ business logic.

**Chứa:**
- `entity/` — đối tượng có identity và lifecycle (Ticket, Event, User)
- `aggregate/` — nhóm entity, có Aggregate Root làm điểm vào duy nhất
- `valueobject/` — đối tượng bất biến, so sánh theo giá trị (Money, SeatNumber)
- `service/` — business logic không thuộc về entity cụ thể nào
- `repository/` — **interface**, định nghĩa contract để lưu/lấy dữ liệu
- `event/` — Domain Event, phát ra khi có điều gì đó quan trọng xảy ra

**Nguyên tắc:** Không import bất kỳ thứ gì từ module khác. Không biết DB là gì.

---

### ticket-application

**Vai trò:** Nhạc trưởng — điều phối luồng xử lý, không chứa business logic.

**Làm gì:**
- Nhận command từ Controller
- Gọi DomainService để xử lý nghiệp vụ
- Gọi Infrastructure để thực hiện side effects (gửi email, push notification...)
- Quản lý transaction

```java
@Service
public class BookingAppServiceImpl implements BookingAppService {

    private final TicketDomainService ticketDomainService; // từ domain
    private final EmailService emailService;               // từ infra

    @Transactional
    public void bookTicket(BookTicketCommand command) {
        // 1. Gọi domain xử lý nghiệp vụ
        Ticket ticket = ticketDomainService.reserve(command.getEventId(), command.getUserId(), command.getQuantity());

        // 2. Gọi infra thực hiện side effects
        emailService.sendConfirmation(ticket);
    }
}
```

---

### ticket-infrastructure

**Vai trò:** Implement các interface mà Domain đã định nghĩa. Làm việc trực tiếp với DB, Redis, Kafka...

**Chứa:**
- `persistence/` — implement `Repository` interface của Domain (JPA, MongoDB...)
- `config/` — cấu hình kỹ thuật (RedisConfig, KafkaConfig...)
- `messaging/` — gửi message qua Kafka, RabbitMQ...
- `external/` — gọi API bên ngoài (payment gateway, SMS...)

```java
@Repository
public class JpaTicketRepositoryImpl implements TicketRepository { // interface từ domain

    @Autowired
    private JpaTicketRepo jpaRepo; // Spring Data JPA

    @Override
    public void save(Ticket ticket) {
        jpaRepo.save(toEntity(ticket)); // convert domain object → JPA entity
    }
}
```

---

### ticket-controller

**Vai trò:** Tiếp nhận HTTP request, gọi AppService, trả về response.

**Nguyên tắc:**
- Không chứa business logic
- Chỉ biết Application Service, không gọi thẳng Domain hay Infra
- Chỉ làm: validate input cơ bản → gọi AppService → format response

```java
@RestController
@RequestMapping("/tickets")
public class TicketController {

    private final BookingAppService bookingAppService; // chỉ biết AppService

    @PostMapping("/book")
    public ResponseEntity<Void> bookTicket(@RequestBody BookTicketRequest request) {
        bookingAppService.bookTicket(request.toCommand());
        return ResponseEntity.ok().build();
    }
}
```

---

### ticket-start

**Vai trò:** Chỉ là điểm khởi động. Không chứa logic gì.

```java
@SpringBootApplication
public class StartApplication {
    public static void main(String[] args) {
        SpringApplication.run(StartApplication.class, args);
    }
}
```

Nhờ Maven transitive dependency, `ticket-start` depends on `ticket-controller` và sẽ kéo theo tất cả các module còn lại khi build.

---

## 4. Mối quan hệ và dependency giữa các layer

### Dependency direction — đọc từ pom.xml

```
ticket-domain       — không phụ thuộc ai
      ▲
      │ (implement interface)
ticket-infrastructure — depends on: ticket-domain
      ▲
      │
ticket-application  — depends on: ticket-domain + ticket-infrastructure
      ▲
      │
ticket-controller   — depends on: ticket-application
      ▲
      │
ticket-start        — depends on: ticket-controller
```

**Quy tắc bất di bất dịch: mũi tên chỉ đi VÀO domain, không bao giờ đi ra.**

---

### Tại sao Domain không phụ thuộc ai?

**Vì Domain là luật chơi — luật chơi không được phép bị ảnh hưởng bởi công nghệ.**

Hãy nghĩ theo góc độ nghiệp vụ:

> "Vé đã dùng thì không được huỷ"
> "Không đủ ghế thì không được đặt"
> "Vé phải được xác nhận trong vòng 15 phút sau khi giữ chỗ"

Những luật này **tồn tại độc lập với công nghệ**. Nó đúng dù mày dùng MySQL hay MongoDB, dù gửi email hay SMS, dù REST hay gRPC. Công nghệ là thứ phục vụ nghiệp vụ — không phải ngược lại.

Nếu Domain phụ thuộc vào Infrastructure, điều gì xảy ra?

```java
// Tình huống: Domain biết JPA
public class TicketDomainService {

    @Autowired
    private JpaTicketRepository repo; // phụ thuộc JPA

    public void cancel(TicketId id) {
        Ticket ticket = repo.findById(id); // gọi DB
        if (ticket.status == USED) throw new Exception("...");
        ticket.status = CANCELLED;
        repo.save(ticket); // gọi DB
    }
}
```

Hệ quả:
- Muốn test logic "vé đã dùng không huỷ được" → **buộc phải có DB** → test chậm, phức tạp
- Muốn đổi sang MongoDB → **phải sửa Domain** — luật nghiệp vụ bị kéo vào thay đổi vì lý do kỹ thuật
- Muốn dùng lại business logic ở chỗ khác (batch job, Kafka consumer...) → **không thể** vì logic đang bám vào JPA

**Giải pháp của DDD:** Domain chỉ định nghĩa *cái nó cần*, không quan tâm *ai cung cấp*:

```java
// Domain nói: "Tao cần thứ này" — viết interface
public interface TicketRepository {
    void save(Ticket ticket);
    Optional<Ticket> findById(TicketId id);
}

// Domain dùng interface, không biết impl là gì
public class TicketDomainService {

    private final TicketRepository repo; // interface!

    public void cancel(TicketId id) {
        Ticket ticket = repo.findById(id).orElseThrow();
        ticket.cancel(); // business logic thuần Java
        repo.save(ticket);
    }
}

// Infrastructure mới biết cụ thể là JPA/Mongo/...
// Domain không biết file này tồn tại
public class JpaTicketRepositoryImpl implements TicketRepository { ... }
```

Test business logic không cần DB:
```java
@Test
void shouldThrowWhenTicketAlreadyUsed() {
    TicketRepository fakeRepo = mock(TicketRepository.class); // fake, không cần DB thật
    when(fakeRepo.findById(any())).thenReturn(Optional.of(usedTicket));

    assertThrows(DomainException.class, () -> domainService.cancel(ticketId));
}
```

**Không có import từ module khác** chỉ là biểu hiện — nguyên nhân thật là: Domain phải được viết như thể công nghệ chưa được chọn.

---

### Ví dụ: Đổi MySQL → MongoDB

```
Trước:  ticket-infrastructure/JpaTicketRepositoryImpl.java  ← XOÁ
Sau:    ticket-infrastructure/MongoTicketRepositoryImpl.java ← THÊM

ticket-domain/TicketRepository.java  ← KHÔNG ĐỤNG
ticket-domain/TicketDomainService.java ← KHÔNG ĐỤNG
ticket-application/BookingAppService.java ← KHÔNG ĐỤNG
```

Domain không biết mày đang dùng MySQL hay MongoDB. Chỉ cần implement interface là xong.

---

### Nguyên tắc nền tảng: Dependency Inversion (chữ D trong SOLID)

```
Không làm thế này:             Làm thế này:
Domain → Infra                 Domain: định nghĩa interface
(domain biết DB)               Infra: implement interface
                               (domain không biết DB)
```

Spring Boot tự inject `impl` vào chỗ cần `interface` lúc runtime — domain không cần biết điều đó.

---

## 5. Các thành phần cốt lõi của DDD

### Entity

Đối tượng có **identity** (ID) và **lifecycle** (trạng thái thay đổi theo thời gian).

```java
public class Ticket {
    private TicketId id;
    private EventId eventId;
    private UserId userId;
    private TicketStatus status; // PENDING, CONFIRMED, CANCELLED, USED
    private int quantity;

    // Business logic nằm TRONG entity
    public void confirm() {
        if (this.status != PENDING) throw new DomainException("Chỉ confirm được vé đang PENDING");
        this.status = CONFIRMED;
    }

    public void cancel() {
        if (this.status == USED) throw new DomainException("Vé đã dùng rồi, không huỷ được");
        this.status = CANCELLED;
    }
}
```

---

### Aggregate Root

Nhóm các Entity liên quan, chỉ có **1 điểm vào duy nhất** để thay đổi trạng thái.

```java
public class Event { // Aggregate Root

    private EventId id;
    private String name;
    private int totalSeats;
    private int availableSeats;
    private List<Ticket> tickets; // Ticket thuộc về Event aggregate

    // Thay đổi Ticket phải đi qua Event, không được sửa Ticket trực tiếp từ bên ngoài
    public Ticket reserveTicket(UserId userId, int quantity) {
        if (availableSeats < quantity) {
            throw new DomainException("Không đủ ghế trống");
        }
        this.availableSeats -= quantity;

        Ticket ticket = new Ticket(TicketId.generate(), this.id, userId, quantity);
        this.tickets.add(ticket);
        return ticket;
    }
}
```

---

### Value Object

Đối tượng **bất biến**, không có identity, so sánh theo **giá trị**.

```java
// Không dùng Long/String primitive
public record TicketId(Long value) {}
public record EventId(Long value) {}

public record Money(BigDecimal amount, String currency) {
    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) throw new DomainException("Khác tiền tệ");
        return new Money(this.amount.add(other.amount), this.currency);
    }
}

public record SeatNumber(String row, int number) {
    // "A12" thay vì String "A12" — có validation, có semantic rõ ràng
}
```

---

### Domain Event

Phát ra khi **điều gì đó quan trọng xảy ra** trong domain. Application layer lắng nghe và xử lý side effects.

```java
// Domain phát ra event
public class TicketBookedEvent {
    private final TicketId ticketId;
    private final UserId userId;
    private final EventId eventId;
    private final LocalDateTime occurredAt;
}

// Application layer lắng nghe
@EventListener
public void onTicketBooked(TicketBookedEvent event) {
    emailService.sendConfirmation(event.getUserId());   // gửi email
    notificationService.push(event.getUserId());         // push notification
    analyticsService.track(event);                       // tracking
}
```

Domain không biết email được gửi hay không — đó không phải việc của nó.

---

### Repository Interface (trong Domain)

Chỉ là **contract** — domain nói "tao cần khả năng này", không quan tâm ai làm.

```java
// Trong ticket-domain — chỉ là interface
public interface TicketRepository {
    void save(Ticket ticket);
    Optional<Ticket> findById(TicketId id);
    List<Ticket> findByEventId(EventId eventId);
}

// Trong ticket-infrastructure — mới biết JPA
@Repository
public class JpaTicketRepositoryImpl implements TicketRepository {
    // implement ở đây
}
```

---

## 6. Flow thực tế: Đặt vé concert

**Request:** `POST /tickets/book { eventId: 1, userId: 42, quantity: 2 }`

```
[HTTP Request]
      │
      ▼
[ticket-controller] TicketController
  - Nhận request
  - Validate format input (không null, quantity > 0)
  - Gọi bookingAppService.bookTicket(command)
      │
      ▼
[ticket-application] BookingAppServiceImpl
  - Lấy Event từ EventRepository
  - Lấy User từ UserRepository
  - Gọi domain để xử lý nghiệp vụ
  - Quản lý transaction
  - Xử lý side effects sau khi thành công
      │                          │
      ▼                          ▼
[ticket-domain]          [ticket-infrastructure]
  Event.reserveTicket()    emailService.send()
  - Kiểm tra còn ghế?      notificationService.push()
  - Kiểm tra hết hạn?
  - Tạo Ticket entity
  - Phát TicketBookedEvent
      │
      ▼
[ticket-domain] TicketRepository (interface)
  - ticket.save(ticket)
      │
      ▼
[ticket-infrastructure] JpaTicketRepositoryImpl
  - Lưu vào DB thật
```

**Ai biết gì:**

| Layer | Biết | Không biết |
|---|---|---|
| Controller | AppService interface | Domain, DB |
| AppService | Domain interface + Infra | HTTP, JSON |
| DomainService | Entity, Repository interface | DB, HTTP, email |
| Infrastructure | DB, Redis, Kafka | Business logic |

---

## 7. Ưu điểm, nhược điểm

### Ưu điểm

**1. Thay đổi infra không ảnh hưởng domain**
Đổi MySQL → MongoDB, thêm Redis cache → chỉ sửa `ticket-infrastructure`.

**2. Test dễ hơn**
Domain test thuần Java, không cần Spring context, không cần DB:
```java
// Test domain không cần @SpringBootTest, không cần H2, không cần mock JPA
@Test
void shouldThrowWhenNoSeatsAvailable() {
    Event event = new Event(totalSeats: 2, availableSeats: 0);
    assertThrows(DomainException.class, () -> event.reserveTicket(userId, 2));
}
```

**3. Rõ trách nhiệm từng layer**
- Controller: HTTP in/out
- Application: điều phối
- Domain: nghiệp vụ
- Infra: kỹ thuật

**4. Scale team**
Team A làm domain (business logic), Team B làm infra (DB, Kafka) — không conflict.

**5. Business logic không bị lẫn vào bất kỳ đâu**
Luật "vé đã dùng không huỷ được" nằm trong `Ticket.cancel()`, tìm 1 chỗ duy nhất.

---

### Nhược điểm

**1. Boilerplate nhiều**
Một feature nhỏ cần: Entity + Repository interface + AppService interface + AppService impl + DomainService + InfraImpl.

**2. Learning curve cao**
Người mới vào khó hiểu tại sao cần nhiều layer như vậy.

**3. Overkill với CRUD đơn giản**
Nếu chỉ là CRUD thông thường (thêm/sửa/xoá), MVC đủ dùng, DDD không mang lại nhiều giá trị.

---

### Khi nào nên dùng DDD?

| Dùng DDD | Không cần DDD |
|---|---|
| Business logic phức tạp (booking, fintech, logistics) | CRUD đơn giản |
| Team lớn, cần tách biệt rõ ràng | Project nhỏ, 1-2 người |
| Thường xuyên đổi infra | Ít thay đổi |
| Cần test business logic độc lập | Deadline gấp, MVP |

**Project booking ticket này: phù hợp dùng DDD** — sẽ có nhiều nghiệp vụ phức tạp: giới hạn số vé, flash sale, hoàn vé, thanh toán, giữ chỗ tạm thời...

---

## 8. Trạng thái hiện tại của project

### Đã có

| Tiêu chí | Trạng thái |
|---|---|
| Layer structure (5 module) | Đúng |
| Dependency direction | Đúng |
| Repository interface trong Domain | Có (HiDomainRepository) |
| AppService tách khỏi DomainService | Có |
| Infrastructure implement Domain interface | Có |

### Còn thiếu (cần implement khi làm feature thật)

| Tiêu chí | Trạng thái |
|---|---|
| Entity (Ticket, Event, User) | Chưa có |
| Aggregate Root | Chưa có |
| Value Object (TicketId, Money...) | Chưa có |
| Domain Event | Chưa có |
| Business logic trong Entity | Chưa có |
| Package tổ chức theo Bounded Context | Chưa có |

### Cấu trúc domain nên hướng tới

```
ticket-domain/
  event/
    entity/Event.java
    valueobject/EventId.java
    repository/EventRepository.java
    service/EventDomainService.java
    event/EventCreatedEvent.java
  ticket/
    entity/Ticket.java
    aggregate/TicketAggregate.java
    valueobject/TicketId.java
    valueobject/TicketStatus.java
    repository/TicketRepository.java
    service/TicketDomainService.java
    event/TicketBookedEvent.java
    event/TicketCancelledEvent.java
  shared/
    valueobject/Money.java
    exception/DomainException.java
```

---

> **Tóm lại một câu:** DDD tách biệt "luật chơi nghiệp vụ" (Domain) khỏi "cách thực thi kỹ thuật" (Infrastructure), giúp code dễ test, dễ thay đổi, và dễ mở rộng khi business logic ngày càng phức tạp.
