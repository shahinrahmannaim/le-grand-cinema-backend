package fr.legrandcinema.auth;

import fr.legrandcinema.shared.ApiException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    public record Register(
            @Email @NotBlank @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 72) String password,
            @NotBlank @Size(max = 100) String displayName) {}

    public record Login(
            @NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 72) String password) {}

    public record Account(UUID id, String email, String displayName, String role) {}

    private record Credential(Account account, String hash) {}

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final SecurityContextRepository contexts;
    private final LoginThrottle throttle;
    private final String dummyHash;

    public AuthController(
            JdbcTemplate jdbc,
            PasswordEncoder encoder,
            SecurityContextRepository contexts,
            LoginThrottle throttle) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.contexts = contexts;
        this.throttle = throttle;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    @GetMapping("/csrf")
    Map<String, String> csrf(CsrfToken token, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    @PostMapping("/register")
    Account register(@Valid @RequestBody Register request, HttpServletRequest http) {
        throttle.check("register:" + http.getRemoteAddr());
        checkPassword(request.password());
        UUID id = UUID.randomUUID();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        jdbc.update(
                "INSERT INTO app_user(id,email,password_hash,display_name,role) VALUES(?,?,?,?,"
                    + " 'CLIENT')",
                id,
                email,
                encoder.encode(request.password()),
                request.displayName().trim());
        return new Account(id, email, request.displayName().trim(), "CLIENT");
    }

    @PostMapping("/login")
    Account login(
            @Valid @RequestBody Login request,
            HttpServletRequest http,
            HttpServletResponse response) {
        throttle.check("login:" + http.getRemoteAddr());
        List<Credential> found =
                jdbc.query(
                        "SELECT * FROM app_user WHERE email=?",
                        (rs, n) ->
                                new Credential(
                                        new Account(
                                                rs.getObject("id", UUID.class),
                                                rs.getString("email"),
                                                rs.getString("display_name"),
                                                rs.getString("role")),
                                        rs.getString("password_hash")),
                        request.email().trim().toLowerCase(Locale.ROOT));
        Credential credential = found.isEmpty() ? null : found.getFirst();
        boolean matches =
                encoder.matches(
                        request.password(), credential == null ? dummyHash : credential.hash());
        if (!matches || credential == null) throw new ApiException(401, "Identifiants incorrects.");
        http.getSession(true);
        http.changeSessionId();
        Account user = credential.account();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        user.id().toString(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.role()))));
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, http, response);
        new HttpSessionCsrfTokenRepository().saveToken(null, http, response);
        response.setHeader("Cache-Control", "no-store");
        return user;
    }

    @GetMapping("/me")
    Account me(Authentication auth, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return jdbc
                .query(
                        "SELECT * FROM app_user WHERE id=?",
                        (rs, n) ->
                                new Account(
                                        rs.getObject("id", UUID.class),
                                        rs.getString("email"),
                                        rs.getString("display_name"),
                                        rs.getString("role")),
                        UUID.fromString(auth.getName()))
                .stream()
                .findFirst()
                .orElseThrow();
    }

    private static void checkPassword(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ApiException(400, "Mot de passe trop long (72 octets maximum).");
    }
}
