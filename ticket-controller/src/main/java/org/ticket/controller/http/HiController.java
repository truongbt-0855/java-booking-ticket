package org.ticket.controller.http;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.ticket.application.service.event.EventAppService;

import java.security.SecureRandom;

@RestController
@RequestMapping("/hello")
@RequiredArgsConstructor
public class HiController {
    private final EventAppService eventAppService;

    private final RestTemplate restTemplate;
    private static final SecureRandom secureRandom = new SecureRandom();

    @GetMapping("/hi")
    @RateLimiter(name = "backendA", fallbackMethod = "sayHiFallback") // backendA là lấy từ file application.yml
    public String sayHi() {
        return eventAppService.sayHi("Truongbt");
    }

    public String sayHiFallback(Throwable throwable) {
        return "Too many request with rateLimiter";
    }

    @GetMapping("/hi/v1")
    @RateLimiter(name = "backendB", fallbackMethod = "sayHiFallback")
    public String sayhello() {
        return eventAppService.sayHi("hello");
    }

    @GetMapping("/circuit/breaker")
    @CircuitBreaker(name = "checkRandom", fallbackMethod = "fallbackCircuitBreaker")
    public String circuitBreaker() {
        int productId = secureRandom.nextInt(20) + 1;
        String url = "https://fakestoreapi.com/products/" + productId;

        return restTemplate.getForObject(url, String.class);
    }

    public String fallbackCircuitBreaker(Throwable throwable) {
        return "Service fakestoreapi Error";
    }
}
