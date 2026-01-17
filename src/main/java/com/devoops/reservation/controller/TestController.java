package com.devoops.reservation.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("api/reservation")
public class TestController {

    private static final Logger logger = LoggerFactory.getLogger(TestController.class);

    @GetMapping("test")
    public String test() {
        String requestId = UUID.randomUUID().toString();
        MDC.put("requestId", requestId);

        try {
            logger.info("Test endpoint called - Reservation Service health check");
            logger.debug("Processing test request with ID: {}", requestId);

            String response = "Reservation Service is up and running!";

            logger.info("Test endpoint successfully processed request {}", requestId);
            return response;

        } finally {
            MDC.remove("requestId");
        }
    }
}
