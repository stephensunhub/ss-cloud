package men.sbb8.grpc.server;

import io.grpc.Channel;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.health.v1.HealthGrpc;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.grpc.client.GrpcChannelFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 使用 Boot 默认的定时同步（首次延迟 5 秒、每 5 秒一次），验证状态变化不需要等定时器
 */
@SpringBootTest
@AutoConfigureTestGrpcTransport
class ImmediateHealthTest {

    @Autowired
    GrpcChannelFactory channels;

    @Autowired
    ApplicationContext context;

    private ServingStatus check(String service) {
        Channel channel = channels.createChannel("test-channel");
        return HealthGrpc.newBlockingStub(channel)
                .check(HealthCheckRequest.newBuilder().setService(service).build()).getStatus();
    }

    @Test
    void servingRightAfterStartup() {
        assertEquals(ServingStatus.SERVING, check("liveness"));
        assertEquals(ServingStatus.SERVING, check("readiness"));
    }

    @Test
    void readinessFlipsImmediatelyWhenRefusingTraffic() {
        try {
            AvailabilityChangeEvent.publish(context, ReadinessState.REFUSING_TRAFFIC);
            assertEquals(ServingStatus.NOT_SERVING, check("readiness"));
            assertEquals(ServingStatus.SERVING, check("liveness"));
        } finally {
            AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC);
            assertEquals(ServingStatus.SERVING, check("readiness"));
        }
    }
}
