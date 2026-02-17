package com.devoops.reservation.grpc;

import com.devoops.reservation.grpc.proto.user.GetUserSummaryRequest;
import com.devoops.reservation.grpc.proto.user.GetUserSummaryResponse;
import com.devoops.reservation.grpc.proto.user.UserInternalServiceGrpc;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class UserGrpcClient {

    @GrpcClient("user-service")
    private UserInternalServiceGrpc.UserInternalServiceBlockingStub userStub;

    public UserSummaryResult getUserSummary(UUID userId) {
        log.debug("Calling user service for user summary: userId={}", userId);

        GetUserSummaryRequest request = GetUserSummaryRequest.newBuilder()
                .setUserId(userId.toString())
                .build();

        GetUserSummaryResponse response = userStub.getUserSummary(request);

        log.debug("Received user summary response: found={}", response.getFound());

        if (!response.getFound()) {
            return new UserSummaryResult(
                    false,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        return new UserSummaryResult(
                true,
                UUID.fromString(response.getUserId()),
                response.getEmail(),
                response.getFirstName(),
                response.getLastName(),
                response.getRole()
        );
    }
}
