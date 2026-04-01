package org.ticket.controller.http;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.ticket.application.model.TicketDetailDTO;
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
    public ResultMessage<TicketDetailDTO> getTicketDetail(
            @PathVariable Long ticketId,
            @PathVariable Long detailId,
            @RequestParam(name = "version", required = false) Long version
    ) {
        return ResultUtil.data(ticketDetailAppService.getTicketDetailById(detailId, version));
    }

    // order - remove local cache
    @GetMapping("/{ticketId}/detail/{detailId}/order")
    public boolean orderTicketByUser(
            @PathVariable Long ticketId,
            @PathVariable Long detailId
    ) {
        return ticketDetailAppService.orderTicketByUser(ticketId);
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
