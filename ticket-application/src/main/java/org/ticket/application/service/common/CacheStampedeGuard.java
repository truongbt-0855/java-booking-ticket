package org.ticket.application.service.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ticket.infrastructure.cache.redis.RedisInfraService;
import org.ticket.infrastructure.distributed.redisson.RedisDistributedLocker;
import org.ticket.infrastructure.distributed.redisson.RedisDistributedService;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Generic utility bảo vệ cache khỏi Cache Stampede.
 *
 * Có thể dùng cho bất kỳ entity nào (Ticket, Booking, Event, Seat...).
 *
 * Flow:
 *  1. Check cache → hit → trả về luôn (no lock)
 *  2. Miss → tranh distributed lock
 *  3. Lấy được lock → double-check cache → vẫn miss → gọi loader (query DB)
 *  4. Set cache (kể cả null → tránh Cache Penetration)
 *  5. Không lấy được lock sau waitTime → trả về null
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheStampedeGuard {

    private static final long DEFAULT_WAIT_TIME  = 1L;
    private static final long DEFAULT_LEASE_TIME = 5L;
    private static final TimeUnit DEFAULT_TIME_UNIT = TimeUnit.SECONDS;

    private final RedisInfraService redisInfraService;
    private final RedisDistributedService redisDistributedService;

    /**
     * @param lockKey  key của distributed lock (phải unique theo entity + id)
     * @param cacheKey key lưu trong Redis
     * @param type     class của object cần deserialize
     * @param loader   hàm query DB — chỉ được gọi khi cache miss và giữ được lock
     * @return         object từ cache hoặc DB, null nếu không lấy được lock hoặc DB trả null
     */
    public <T> T protect(String lockKey, String cacheKey, Class<T> type, Supplier<T> loader) {
        // Bước 1: Check cache — happy path, không cần lock
        T value = redisInfraService.getObject(cacheKey, type);
        if (value != null) {
            return value;
        }

        // Bước 2: Cache miss → tranh lock
        RedisDistributedLocker locker = redisDistributedService.getDistributedLock(lockKey);
        try {
            boolean isLock = locker.tryLock(DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME, DEFAULT_TIME_UNIT);
            if (!isLock) {
                log.warn("Could not acquire lock for key={}, returning null", lockKey);
                return null;
            }

            // Bước 3: Double-check — request trước vừa set cache trong lúc ta chờ lock
            value = redisInfraService.getObject(cacheKey, type);
            if (value != null) {
                return value;
            }

            // Bước 4: Vẫn miss → gọi loader (query DB)
            value = loader.get();
            log.info("Loaded from DB: lockKey={}, result={}", lockKey, value);

            // Bước 5: Set cache kể cả null (null-value caching) → tránh Cache Penetration
            redisInfraService.setObject(cacheKey, value);
            return value;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for lock: " + lockKey, e);
        } finally {
            locker.unlock();
        }
    }
}
