package com.dawnrise.identity.notification.service;

import com.dawnrise.identity.notification.dto.NotificationItemResponse;
import com.dawnrise.identity.notification.model.NotificationCategory;
import com.dawnrise.identity.notification.model.NotificationFilter;
import com.dawnrise.identity.user.repository.UserRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationInboxQueryTest {

    @ParameterizedTest
    @MethodSource("filtersAndCategories")
    void buildsValidSuppressionAwareQueries(NotificationFilter filter,
                                             NotificationCategory category,
                                             String expectedFilterCondition) {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<NotificationItemResponse>>any()))
                .thenReturn(List.of());
        NotificationServiceImpl service = new NotificationServiceImpl(jdbc, mock(UserRepository.class));

        service.inbox(1L, 10L, filter, category, 0, 20);

        var countSql = org.mockito.ArgumentCaptor.forClass(String.class);
        var contentSql = org.mockito.ArgumentCaptor.forClass(String.class);
        var contentParameters = org.mockito.ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).queryForObject(countSql.capture(), any(MapSqlParameterSource.class), eq(Long.class));
        verify(jdbc).query(contentSql.capture(), contentParameters.capture(),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<NotificationItemResponse>>any());
        assertTrue(countSql.getValue().contains("AND NOT r.inbox_suppressed"));
        assertTrue(contentSql.getValue().contains("AND NOT r.inbox_suppressed"));
        assertTrue(contentSql.getValue().contains(expectedFilterCondition));
        assertTrue(contentSql.getValue().matches("(?s).*\\n\\s*ORDER BY.*"));
        assertFalse(contentSql.getValue().contains(":categoryORDER"));
        if (category == null) {
            assertFalse(contentParameters.getValue().hasValue("category"));
        } else {
            assertEquals(category.name(), contentParameters.getValue().getValue("category"));
            assertTrue(contentSql.getValue().contains("AND e.category=:category\n"));
        }
    }

    private static Stream<Arguments> filtersAndCategories() {
        return Stream.of(
                Arguments.of(NotificationFilter.ALL, null, "AND NOT r.inbox_suppressed"),
                Arguments.of(NotificationFilter.ALL, NotificationCategory.TIMETABLE,
                        "AND e.category=:category"),
                Arguments.of(NotificationFilter.UNREAD, null, "AND r.read_at IS NULL"),
                Arguments.of(NotificationFilter.UNREAD, NotificationCategory.TIMETABLE,
                        "AND r.read_at IS NULL"),
                Arguments.of(NotificationFilter.IMPORTANT, null,
                        "AND e.importance IN ('IMPORTANT','URGENT')"),
                Arguments.of(NotificationFilter.IMPORTANT, NotificationCategory.TIMETABLE,
                        "AND e.importance IN ('IMPORTANT','URGENT')"));
    }
}
