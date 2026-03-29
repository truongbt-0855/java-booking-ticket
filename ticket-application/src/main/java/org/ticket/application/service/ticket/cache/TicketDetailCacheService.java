package org.ticket.application.service.ticket.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ticket.domain.model.entity.TicketDetail;
import org.ticket.domain.service.TicketDetailDomainService;
import org.ticket.infrastructure.cache.redis.RedisInfraService;
import org.ticket.infrastructure.distributed.redisson.RedisDistributedLocker;
import org.ticket.infrastructure.distributed.redisson.RedisDistributedService;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class TicketDetailCacheService {
    private final RedisDistributedService redisDistributedService;
    private final RedisInfraService redisInfraService;
    private final TicketDetailDomainService ticketDetailDomainService;

    /**
     * Cache-aside pattern cơ bản: check Redis → miss → query DB → set cache.
     *
     * Vấn đề (Cache Stampede): nếu cache expire và 1000 request cùng miss,
     * tất cả đồng loạt query DB → DB quá tải.
     * Dùng getTicketDefaultCacheVip() để giải quyết vấn đề này.
     *
     * Vấn đề thứ 2 (Cache Penetration): nếu DB trả về null (ticketDetail không tồn tại),
     * code hiện tại không set cache → mọi request đều bypass cache và hit DB mãi mãi.
     * Fix: set cache kể cả khi null (null-value caching), xem getTicketDefaultCacheVip().
     */
    public TicketDetail getTicketDefaultCacheNormal(Long id, Long version) {
        TicketDetail ticketDetail = redisInfraService.getObject(genEventItemKey(id), TicketDetail.class);
        if (ticketDetail != null) {
            log.info("FROM CACHE {}, {}, {}", id, version, ticketDetail);
            return ticketDetail;
        }

        ticketDetail = ticketDetailDomainService.getTicketDetailById(id);
        log.info("FROM DBS {}, {}, {}", id, version, ticketDetail);

        if (ticketDetail != null) {
            redisInfraService.setObject(genEventItemKey(id), ticketDetail);
        }
        return ticketDetail;
    }

    /**
     * Cache-aside pattern với Distributed Lock — giải quyết Cache Stampede.
     *
     * Flow:
     *  1. Check cache → có thì trả về luôn (happy path, không cần lock)
     *  2. Cache miss → tranh Distributed Lock theo từng ticketId
     *  3. Lấy được lock → double-check cache (request trước có thể đã set xong)
     *     → vẫn miss → query DB → set cache (kể cả null để tránh Cache Penetration)
     *  4. Không lấy được lock sau 1s → trả về null
     *     → caller (Application layer) có trách nhiệm retry hoặc trả lỗi cho client.
     *
     * TODO: Trường hợp trả về null khi không lấy được lock chưa được xử lý ở Application layer.
     *       Cần implement retry logic tại TicketDetailAppServiceImpl.
     */
    public TicketDetail getTicketDefaultCacheVip(Long id, Long version) {
        log.info("getTicketDefaultCacheVip id={}, version={}", id, version);

        // Bước 1: Check cache — không cần lock nếu đã có dữ liệu
        TicketDetail ticketDetail = redisInfraService.getObject(genEventItemKey(id), TicketDetail.class);
        if (ticketDetail != null) {
            return ticketDetail;
        }

        // Bước 2: Cache miss → tranh lock theo từng ticketId (không dùng 1 lock chung để tránh bottleneck)
        RedisDistributedLocker locker = redisDistributedService.getDistributedLock("PRO_LOCK_KEY_ITEM" + id);
        try {
            // waitTime=1s: chờ tối đa 1s, leaseTime=5s: tự release nếu server crash
            boolean isLock = locker.tryLock(1, 5, TimeUnit.SECONDS);
            if (!isLock) {
                // Không lấy được lock sau 1s → trả null, Application layer phải xử lý
                return null;
            }

            // Bước 3: Double-check cache — request trước vừa set xong trong lúc ta chờ lock
            ticketDetail = redisInfraService.getObject(genEventItemKey(id), TicketDetail.class);
            if (ticketDetail != null) {
                return ticketDetail;
            }

            // Bước 4: Vẫn miss → query DB, chỉ 1 request duy nhất vào đây tại 1 thời điểm
            ticketDetail = ticketDetailDomainService.getTicketDetailById(id);
            log.info("FROM DBS id={}, result={}", id, ticketDetail);

            // Bước 5: Set cache kể cả khi null (null-value caching) — tránh Cache Penetration:
            // nếu không set null, mọi request với id không tồn tại sẽ bypass cache và hit DB mãi mãi
            redisInfraService.setObject(genEventItemKey(id), ticketDetail);
            return ticketDetail;

        } catch (InterruptedException e) {
            // Thread bị ngắt trong khi chờ lock — restore interrupt flag để caller biết
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for lock: " + id, e);
        } finally {
            locker.unlock(); // Bắt buộc đặt trong finally — đảm bảo release dù có exception
        }
    }

    private String genEventItemKey(Long itemId) {
        return "PRO_TICKET:ITEM:" + itemId;
    }
}
