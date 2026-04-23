package com.tool.otsutil.auth;

import com.tool.otsutil.config.LoginAuthInterceptor;
import com.tool.otsutil.controller.AuthController;
import com.tool.otsutil.controller.InspectionController;
import com.tool.otsutil.exception.ExceptionCatch;
import com.tool.otsutil.service.InspectionImpl.InspectionQueryService;
import com.tool.otsutil.service.InspectionImpl.InspectionService;
import com.tool.otsutil.service.InspectionImpl.InspectionTableService;
import com.tool.otsutil.service.InspectionImpl.TuMoStatisticsService;
import com.tool.otsutil.service.auth.AuthService;
import com.tool.otsutil.serverconnection.controller.ServerConnectionController;
import com.tool.otsutil.serverconnection.service.TerminalSessionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFlowTest {

    private MockMvc mockMvc;

    private InspectionService inspectionService;

    private TuMoStatisticsService tuMoStatisticsService;

    private InspectionTableService inspectionTableService;

    private InspectionQueryService inspectionQueryService;

    private TerminalSessionManager terminalSessionManager;

    @BeforeEach
    void setUp() {
        inspectionService = mock(InspectionService.class);
        tuMoStatisticsService = mock(TuMoStatisticsService.class);
        inspectionTableService = mock(InspectionTableService.class);
        inspectionQueryService = mock(InspectionQueryService.class);
        terminalSessionManager = mock(TerminalSessionManager.class);

        given(inspectionQueryService.getDashboardSummary()).willReturn(null);
        given(terminalSessionManager.listServers()).willReturn(Collections.emptyList());

        AuthService authService = new AuthService();

        InspectionController inspectionController = new InspectionController();
        ReflectionTestUtils.setField(inspectionController, "inspectionService", inspectionService);
        ReflectionTestUtils.setField(inspectionController, "tuMoStatisticsService", tuMoStatisticsService);
        ReflectionTestUtils.setField(inspectionController, "inspectionTableService", inspectionTableService);
        ReflectionTestUtils.setField(inspectionController, "inspectionQueryService", inspectionQueryService);
        ReflectionTestUtils.setField(inspectionController, "fileName", "test.xlsx");
        ReflectionTestUtils.setField(inspectionController, "exportPath", ".");

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AuthController(authService),
                        inspectionController,
                        new ServerConnectionController(terminalSessionManager)
                )
                .addMappedInterceptors(
                        new String[]{"/Inspection/**", "/server-connections/**"},
                        new LoginAuthInterceptor(authService)
                )
                .setControllerAdvice(new ExceptionCatch())
                .build();
    }

    @Test
    void shouldLoginProbeProtectedEndpointsAndLogout() throws Exception {
        mockMvc.perform(get("/Inspection/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        mockMvc.perform(get("/server-connections/servers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"JCDZ@is.01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andReturn()
                .getRequest()
                .getSession(false);

        mockMvc.perform(get("/auth/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("admin"));

        mockMvc.perform(get("/Inspection/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/server-connections/servers").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/auth/logout").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/auth/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    void shouldRejectInvalidPassword() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2));
    }
}
