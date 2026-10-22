package men.sbb8.grpc.starter;

import io.grpc.Channel;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.health.v1.HealthGrpc;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.grpc.client.GrpcChannelFactory;

import java.time.Duration;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(properties = {
        "spring.grpc.server.health.schedule.delay=0s",
        "spring.grpc.server.health.schedule.period=100ms"
})
@AutoConfigureTestGrpcTransport
class HealthProbeTest {

    @Autowired
    GrpcChannelFactory channels;

    @Autowired
    ApplicationContext context;

    private ServingStatus check(String service) {
        Channel channel = channels.createChannel("test-channel");
        return HealthGrpc.newBlockingStub(channel)
                .check(HealthCheckRequest.newBuilder().setService(service).build()).getStatus();
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                fail("等待健康状态变化超时");
            }
            Thread.sleep(50);
        }
    }

    @Test
    void livenessAndReadinessServicesAreServing() throws Exception {
        await(() -> check("liveness") == ServingStatus.SERVING && check("readiness") == ServingStatus.SERVING);
        assertEquals(ServingStatus.SERVING, check(""));
    }

    @Test
    void readinessFlipsWithoutAffectingLiveness() throws Exception {
        try {
            AvailabilityChangeEvent.publish(context, ReadinessState.REFUSING_TRAFFIC);
            await(() -> check("readiness") == ServingStatus.NOT_SERVING);
            assertEquals(ServingStatus.SERVING, check("liveness"));
        } finally {
            AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC);
            await(() -> check("readiness") == ServingStatus.SERVING);
        }
    }

    @Test
    void livenessFlipsWhenApplicationIsBroken() throws Exception {
        try {
            AvailabilityChangeEvent.publish(context, LivenessState.BROKEN);
            await(() -> check("liveness") == ServingStatus.NOT_SERVING);
        } finally {
            AvailabilityChangeEvent.publish(context, LivenessState.CORRECT);
            await(() -> check("liveness") == ServingStatus.SERVING);
        }
    }

    @Test
    void unknownServiceIsNotFound() {
        assertThrows(StatusRuntimeException.class, () -> check("no-such-service"));
    }
}
