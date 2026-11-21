package fr.legrandcinema.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fr.legrandcinema.shared.ApiException;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class StaffControllerTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final StaffController controller = new StaffController(jdbc);

    @Test
    void searchBindsReferenceAndScreeningInsteadOfInterpolatingUserInput() {
        String input = "' OR 1=1--";
        UUID screening = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("reference", "GC-ABC")));
        Map<String, Object> result = controller.search(input, screening, 2);
        assertEquals(1L, result.get("total"));
        ArgumentCaptor<String> countSql = ArgumentCaptor.forClass(String.class);
        verify(jdbc)
                .queryForObject(
                        countSql.capture(),
                        eq(Long.class),
                        eq(input),
                        eq(input),
                        eq(screening),
                        eq(screening));
        ArgumentCaptor<String> rowsSql = ArgumentCaptor.forClass(String.class);
        verify(jdbc)
                .queryForList(
                        rowsSql.capture(),
                        eq(input),
                        eq(input),
                        eq(screening),
                        eq(screening),
                        eq(50));
        assertFalse(countSql.getValue().contains(input));
        assertFalse(rowsSql.getValue().contains(input));
    }

    @Test
    void rejectsInvalidPaginationBeforeQuerying() {
        assertEquals(
                400,
                assertThrows(ApiException.class, () -> controller.search("", null, -1)).status());
        assertEquals(
                400,
                assertThrows(ApiException.class, () -> controller.search("", null, 100001))
                        .status());
        assertEquals(
                400,
                assertThrows(ApiException.class, () -> controller.search("x".repeat(33), null, 0))
                        .status());
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsInvalidOccupancyPeriodAndThreshold() {
        LocalDate from = LocalDate.of(2026, 11, 20);
        assertThrows(ApiException.class, () -> controller.occupancy(from, from.minusDays(1), 0));
        assertThrows(ApiException.class, () -> controller.occupancy(from, from.plusDays(366), 0));
        assertThrows(ApiException.class, () -> controller.occupancy(from, from, -1));
        assertThrows(ApiException.class, () -> controller.occupancy(from, from, 101));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsIncompleteOverlappingOrUnequalComparisonPeriods() {
        LocalDate from = LocalDate.of(2026, 11, 20);
        assertThrows(
                ApiException.class,
                () -> controller.analytics(from, from.plusDays(2), from.minusDays(7), null));
        assertThrows(
                ApiException.class,
                () -> controller.analytics(from, from.plusDays(2), null, from.minusDays(5)));
        assertThrows(
                ApiException.class,
                () ->
                        controller.analytics(
                                from, from.plusDays(2), from.minusDays(7), from.minusDays(7)));
        assertThrows(
                ApiException.class,
                () ->
                        controller.analytics(
                                from, from.plusDays(2), from.minusDays(1), from.plusDays(1)));
        verifyNoInteractions(jdbc);
    }
}
