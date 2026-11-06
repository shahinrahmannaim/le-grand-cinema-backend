package fr.legrandcinema.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** Explicit demo profile only; no real customers or password in source control. */
@Component
@Profile("demo")
public class DemoAccounts implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final String password;

    public DemoAccounts(
            JdbcTemplate jdbc,
            PasswordEncoder encoder,
            @Value("${DEMO_PASSWORD:}") String password) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalStateException(
                    "Le profil demo exige DEMO_PASSWORD de 12 à 72 octets. Ne pas réutiliser un"
                        + " vrai mot de passe.");
        for (String role : new String[] {"ADMIN", "STAFF", "CLIENT"}) {
            jdbc.update(
                    "INSERT INTO app_user(email,password_hash,display_name,role) VALUES(?,?,?,?) ON"
                        + " CONFLICT(email) DO NOTHING",
                    role.toLowerCase() + "@demo.cinema.test",
                    encoder.encode(password),
                    "Démo " + role,
                    role);
        }
        jdbc.update(
                """
                INSERT INTO screening(film_id,room_id,starts_at,ends_at,occupied_until,price_cents)
                SELECT f.id,r.id,t.starts,t.starts+make_interval(mins=>f.duration_minutes),
                    t.starts+make_interval(mins=>f.duration_minutes+r.turnaround_minutes),1100
                FROM (SELECT id,duration_minutes,row_number() OVER(ORDER BY id) AS n FROM film
                    WHERE id IN ('10000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000003') AND published) f
                JOIN (SELECT id,turnaround_minutes,row_number() OVER(ORDER BY name) AS n FROM room) r ON r.n=f.n
                CROSS JOIN generate_series(0,29) AS day_offset CROSS JOIN (VALUES(10),(14),(18)) slot(hour)
                CROSS JOIN LATERAL(SELECT (((current_timestamp AT TIME ZONE 'Europe/Paris')::date+day_offset)::timestamp
                    +make_interval(hours=>slot.hour)) AT TIME ZONE 'Europe/Paris' AS starts) t
                WHERE t.starts>current_timestamp AND NOT EXISTS(SELECT 1 FROM screening s WHERE s.film_id=f.id AND s.room_id=r.id AND s.starts_at=t.starts)
                ON CONFLICT DO NOTHING
                """);
    }
}
