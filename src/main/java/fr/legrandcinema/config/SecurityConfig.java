package fr.legrandcinema.config;

import jakarta.servlet.DispatcherType;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Configuration
public class SecurityConfig {
    @Bean
    UserDetailsService userDetailsService(JdbcTemplate jdbc) {
        return email ->
                jdbc
                        .query(
                                "SELECT id,password_hash,role FROM app_user WHERE email=?",
                                (rs, n) ->
                                        User.withUsername(rs.getString("id"))
                                                .password(rs.getString("password_hash"))
                                                .roles(rs.getString("role"))
                                                .build(),
                                email.trim().toLowerCase(Locale.ROOT))
                        .stream()
                        .findFirst()
                        .orElseThrow(() -> new UsernameNotFoundException("Compte introuvable"));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository repository)
            throws Exception {
        return http.cors(Customizer.withDefaults())
                .securityContext(c -> c.securityContextRepository(repository))
                .authorizeHttpRequests(
                        auth ->
                                auth.dispatcherTypeMatchers(
                                                DispatcherType.ERROR, DispatcherType.ASYNC)
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/actuator/health",
                                                "/api/catalog/**",
                                                "/api/auth/csrf")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/auth/register",
                                                "/api/auth/login")
                                        .permitAll()
                                        .requestMatchers("/api/admin/**")
                                        .hasRole("ADMIN")
                                        .requestMatchers("/api/staff/**")
                                        .hasAnyRole("ADMIN", "STAFF")
                                        .requestMatchers("/api/**")
                                        .authenticated()
                                        .anyRequest()
                                        .denyAll())
                .exceptionHandling(
                        e ->
                                e.authenticationEntryPoint((q, s, x) -> s.sendError(401))
                                        .accessDeniedHandler((q, s, x) -> s.sendError(403)))
                .logout(
                        l ->
                                l.logoutUrl("/api/auth/logout")
                                        .logoutSuccessHandler((q, s, a) -> s.setStatus(204)))
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${cinema.allowed-origins}") String origins) {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).toList());
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("Accept", "Content-Type", "X-CSRF-TOKEN"));
        c.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", c);
        return source;
    }
}
