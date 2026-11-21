package fr.legrandcinema.config;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Locale;

class DemoAccountsTest {
    @Test
    @ResourceLock("default-locale")
    void demoLoginNamesDoNotDependOnServerLocale() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(anyString())).thenReturn("encoded-test-password");
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            new DemoAccounts(jdbc, encoder, "test-only-password")
                    .run(new DefaultApplicationArguments());
            String insert =
                    "INSERT INTO app_user(email,password_hash,display_name,role) VALUES(?,?,?,?) ON"
                            + " CONFLICT(email) DO NOTHING";
            verify(jdbc)
                    .update(
                            insert,
                            "admin@demo.cinema.test",
                            "encoded-test-password",
                            "Démo ADMIN",
                            "ADMIN");
            verify(jdbc)
                    .update(
                            insert,
                            "client@demo.cinema.test",
                            "encoded-test-password",
                            "Démo CLIENT",
                            "CLIENT");
        } finally {
            Locale.setDefault(previous);
        }
    }
}
