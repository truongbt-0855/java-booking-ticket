package org.ticket.controller.resource;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ticket.application.service.event.EventAppService;

@RestController
@RequestMapping("/hello")
@RequiredArgsConstructor
public class HiController {
    private final EventAppService eventAppService;

    @GetMapping("/hi")
    public String sayHi() {
        return eventAppService.sayHi("Truongbt");
    }
}
