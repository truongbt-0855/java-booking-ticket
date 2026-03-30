package org.ticket.controller.http;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ticket.application.service.ticket.TicketDetailAppService;
import org.ticket.controller.model.enums.ResultUtil;
import org.ticket.controller.model.vo.ResultMessage;
import org.ticket.domain.model.entity.TicketDetail;

@RestController
@RequestMapping("/ticket")
@Slf4j
@RequiredArgsConstructor
public class TicketDetailController {
    // CALL Service Application
    private final TicketDetailAppService ticketDetailAppService;

    // ===== CÁCH CŨ: CacheService tự handle lock + cache (vi phạm SRP) =====
    @GetMapping("/{ticketId}/detail/{detailId}")
    public ResultMessage<TicketDetail> getTicketDetail(
            @PathVariable Long ticketId,
            @PathVariable Long detailId
    ) {
        log.info("MEMBER TIPS GO");
        log.info(" ticketId:{}, detailId:{}", ticketId, detailId);
        return ResultUtil.data(ticketDetailAppService.getTicketDetailById(detailId));
    }

    // ===== CÁCH MỚI: dùng CacheStampedeGuard — SRP, reusable =====
    @GetMapping("/{ticketId}/detail_refactor/{detailId}")
    public ResultMessage<TicketDetail> getTicketDetailRefactor(
            @PathVariable Long ticketId,
            @PathVariable Long detailId
    ) {
        log.info("REFACTOR VERSION");
        log.info(" ticketId:{}, detailId:{}", ticketId, detailId);
        return ResultUtil.data(ticketDetailAppService.getTicketDetailByIdV2(detailId));
    }
}
