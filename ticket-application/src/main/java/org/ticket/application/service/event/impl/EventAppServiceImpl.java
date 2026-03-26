package org.ticket.application.service.event.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.ticket.application.service.event.EventAppService;
import org.ticket.domain.service.HiDomainService;

@Service
@RequiredArgsConstructor
public class EventAppServiceImpl implements EventAppService {
    // Call domain service
    private final HiDomainService hiDomainService;

    @Override
    public String sayHi(String who) {
        return hiDomainService.sayHi(who);
    }
}
