package fr.legrandcinema.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import fr.legrandcinema.shared.ApiException;

import org.junit.jupiter.api.Test;

import java.time.Clock;

class LoginThrottleTest {
    @Test
    void limitsRepeatedAttemptsButDoesNotBlockAnotherKey() {
        LoginThrottle throttle = new LoginThrottle(Clock.systemUTC());
        for (int i = 0; i < 20; i++) throttle.check("login:client-a");
        assertEquals(
                429,
                assertThrows(ApiException.class, () -> throttle.check("login:client-a")).status());
        throttle.check("login:client-b");
    }
}
