package com.devoops.reservation.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/reservation")
public class TestController {

    @GetMapping("test")
    public String test() {
        return "Reservation Service is up and running!";
    }

}
