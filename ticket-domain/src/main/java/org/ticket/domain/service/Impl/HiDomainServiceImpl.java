package org.ticket.domain.service.Impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.ticket.domain.repository.HiDomainRepository;
import org.ticket.domain.service.HiDomainService;

@Service
@RequiredArgsConstructor
public class HiDomainServiceImpl implements HiDomainService {
    private final HiDomainRepository hiDomainRepository;

    @Override
    public String sayHi(String who) {
        return hiDomainRepository.sayHi("From Domain " + who);
    }
}
