package org.ticket.infrastructure.persistence.repository;

import org.springframework.stereotype.Service;
import org.ticket.domain.repository.HiDomainRepository;

@Service
public class HiInfraRepositoryImpl implements HiDomainRepository {
    @Override
    public String sayHi(String who) {
        return "Hi Infrastructure with " + who;
    }
}
