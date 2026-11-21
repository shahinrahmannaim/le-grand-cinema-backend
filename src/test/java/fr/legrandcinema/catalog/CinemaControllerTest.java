package fr.legrandcinema.catalog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.legrandcinema.config.SecurityConfig;

import java.sql.ResultSet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = CinemaController.class,
        properties = "cinema.allowed-origins=http://localhost:4200")
@Import(SecurityConfig.class)
class CinemaControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean JdbcTemplate jdbc;

    @Test
    void anonymousVisitorReceivesCinemaJsonAtRoot() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("room_count")).thenReturn(10);
        when(rs.getInt("seat_capacity")).thenReturn(1700);

        doAnswer(
                invocation -> {
                    RowMapper<CinemaController.CinemaDetails> mapper =
                            invocation.getArgument(1);
                    return mapper.mapRow(rs, 0);
                })
                .when(jdbc)
                .queryForObject(
                        anyString(), anyRowMapper());

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value("Le Grand Cinéma"))
                .andExpect(jsonPath("$.roomCount").value(10))
                .andExpect(jsonPath("$.seatCapacity").value(1700))
                .andExpect(jsonPath("$.timeZone").value("Europe/Paris"))
                .andExpect(jsonPath("$.documentation").value("/api/docs/index.html"));
    }

    @Test
    void anonymousVisitorStillCannotAccessAdministration() throws Exception {
        mvc.perform(get("/api/admin/films")).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousVisitorStillCannotAccessPrivateTickets() throws Exception {
        mvc.perform(get("/api/reservations")).andExpect(status().isUnauthorized());
    }

    private static RowMapper<CinemaController.CinemaDetails> anyRowMapper() {
        return any();
    }
}