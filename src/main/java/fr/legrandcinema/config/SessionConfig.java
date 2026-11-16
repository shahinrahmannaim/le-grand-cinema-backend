package fr.legrandcinema.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@EnableJdbcHttpSession(maxInactiveIntervalInSeconds = 1800)
public class SessionConfig {
    @Bean
    DefaultCookieSerializer cookieSerializer(
            @Value("${cinema.secure-cookies:false}") boolean secure) {
        DefaultCookieSerializer cookie = new DefaultCookieSerializer();
        cookie.setCookieName("CINEMA_SESSION");
        cookie.setCookiePath("/api");
        cookie.setUseHttpOnlyCookie(true);
        cookie.setUseSecureCookie(secure);
        cookie.setSameSite("Lax");
        return cookie;
    }
}
