package com.devoops.reservation.config;

import java.util.UUID;

public record UserContext(UUID userId, String role) {}