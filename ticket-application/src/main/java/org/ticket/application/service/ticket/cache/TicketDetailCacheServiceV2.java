package org.ticket.application.service.ticket.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ticket.application.service.common.CacheStampedeGuard;
import org.ticket.domain.model.entity.TicketDetail;
import org.ticket.domain.service.TicketDetailDomainService;

/**
 * Refactored version của TicketDetailCacheService — so sánh với bản cũ để thấy sự khác nhau.
 *
 * Điểm khác biệt:
 *  - Không còn phụ thuộc vào RedisDistributedService, RedisInfraService trực tiếp
 *  - Toàn bộ lock + double-check logic được delegate sang CacheStampedeGuard
 *  - Class này chỉ còn 1 trách nhiệm: định nghĩa cache key và gọi domain service
 *
 * Lợi ích:
 *  - CacheStampedeGuard có thể tái dùng cho BookingCacheService, EventCacheService, SeatCacheService...
 *  - Muốn đổi lock strategy chỉ sửa CacheStampedeGuard, không đụng vào các CacheService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketDetailCacheServiceV2 {

    private static final String LOCK_PREFIX  = "PRO_LOCK_KEY_ITEM:";
    private static final String CACHE_PREFIX = "PRO_TICKET:ITEM:";

    private final CacheStampedeGuard cacheStampedeGuard;
    private final TicketDetailDomainService ticketDetailDomainService;

    public TicketDetail getTicketDetail(Long id) {
        return cacheStampedeGuard.protect(
                LOCK_PREFIX + id,
                CACHE_PREFIX + id,
                TicketDetail.class,
                () -> ticketDetailDomainService.getTicketDetailById(id)
        );
    }
}
