package com.railyatra.admin;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.railyatra.auth.JwtAuthenticationFilter;
import com.railyatra.auth.JwtService;
import com.railyatra.auth.UserRepository;
import com.railyatra.auth.User;
import com.railyatra.auth.AuthPrincipal;
import com.railyatra.common.SecurityConfig;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@WebMvcTest(AdminDashboardController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AdminDashboardSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean AdminDashboardService dashboard;
    @MockBean AdminDashboardEvents events;
    @MockBean JwtService jwt;
    @MockBean UserRepository users;

    @Test void anonymousCannotReadStatisticsOrSubscribe() throws Exception {
        mvc.perform(get("/api/admin/dashboard")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/admin/dashboard/events")).andExpect(status().is4xxClientError());
        verifyNoInteractions(dashboard, events);
    }
    @Test @WithMockUser(roles = "USER")
    void regularUserCannotReadStatisticsOrSubscribe() throws Exception {
        mvc.perform(get("/api/admin/dashboard")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/dashboard/events")).andExpect(status().isForbidden());
        verifyNoInteractions(dashboard, events);
    }
    @Test @WithMockUser(roles = "ADMIN")
    void adminCanReadSnapshotAndOpenPrivateStream() throws Exception {
        when(dashboard.snapshot()).thenReturn(new AdminDashboardResponse(LocalDate.of(2026,10,10), "Asia/Kolkata",
            5100,650,12,7,2,3,1,new BigDecimal("800.00"),new BigDecimal("1000.00"),new BigDecimal("200.00"),4));
        mvc.perform(get("/api/admin/dashboard")).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.totalTrains").value(5100)).andExpect(jsonPath("$.revenue").value(800));
        var emitter = new SseEmitter(60_000L);
        when(events.subscribe()).thenReturn(emitter);
        mvc.perform(get("/api/admin/dashboard/events")).andExpect(status().isOk())
            .andExpect(request().asyncStarted()).andExpect(header().string("Cache-Control", "no-store"));
        emitter.complete();
    }

    @Test void adminRoleClaimCannotOverrideCurrentDatabaseRole() throws Exception {
        var user = new User("Traveller", "role-check@test.local", "9000000002", "test-hash");
        ReflectionTestUtils.setField(user, "id", 42L);
        when(jwt.parseAccessToken("old-admin-token")).thenReturn(new AuthPrincipal(42L, user.getEmail(), "ROLE_ADMIN"));
        when(users.findById(42L)).thenReturn(Optional.of(user)); // Currently ROLE_USER.
        mvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer old-admin-token"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/dashboard/events").header("Authorization", "Bearer old-admin-token"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(dashboard, events);
    }
}
