package com.example.jwtauthenticator.controller;

import com.example.jwtauthenticator.dto.CheckUsernameRequest;
import com.example.jwtauthenticator.dto.SimpleCheckUsernameRequest;
import com.example.jwtauthenticator.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import com.example.jwtauthenticator.config.TestSecurityConfig;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
public class AuthControllerCheckUsernameTest {

    @Autowired
    private WebApplicationContext context;
    
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;
    
    @org.junit.jupiter.api.BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    @WithMockUser
    public void testCheckUsernameExists() throws Exception {
        // Given
        CheckUsernameRequest request = new CheckUsernameRequest("existingUser", "brand1");
        when(authService.checkUsernameExists(any(CheckUsernameRequest.class))).thenReturn(true);

        // When & Then
        mockMvc.perform(post("/auth/check-username")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.message").value("Username is already taken"));
    }

    @Test
    @WithMockUser
    public void testCheckUsernameDoesNotExist() throws Exception {
        // Given
        CheckUsernameRequest request = new CheckUsernameRequest("newUser", "brand1");
        when(authService.checkUsernameExists(any(CheckUsernameRequest.class))).thenReturn(false);

        // When & Then
        mockMvc.perform(post("/auth/check-username")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(false))
                .andExpect(jsonPath("$.message").value("Username is available"));
    }

    @Test
    @WithMockUser
    public void testCheckUsernameInvalidRequest() throws Exception {
        // Given
        CheckUsernameRequest request = new CheckUsernameRequest("", "");

        // When & Then
        mockMvc.perform(post("/auth/check-username")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    @WithMockUser
    public void testSimpleCheckUsernameExists() throws Exception {
        // Given
        SimpleCheckUsernameRequest request = new SimpleCheckUsernameRequest("existingUser");
        when(authService.checkUsernameExists(any(SimpleCheckUsernameRequest.class))).thenReturn(true);

        // When & Then
        mockMvc.perform(post("/auth/check-username/simple")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.message").value("Username is already taken"));
    }

    @Test
    @WithMockUser
    public void testSimpleCheckUsernameDoesNotExist() throws Exception {
        // Given
        SimpleCheckUsernameRequest request = new SimpleCheckUsernameRequest("newUser");
        when(authService.checkUsernameExists(any(SimpleCheckUsernameRequest.class))).thenReturn(false);

        // When & Then
        mockMvc.perform(post("/auth/check-username/simple")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(false))
                .andExpect(jsonPath("$.message").value("Username is available"));
    }
    
    @Test
    @WithMockUser
    public void testGetUsernameExists() throws Exception {
        // Given
        when(authService.checkUsernameExists(anyString())).thenReturn(true);

        // When & Then
        mockMvc.perform(get("/auth/username-exists")
                .param("username", "existingUser")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.message").value("Username is already taken"))
                .andExpect(jsonPath("$.username").value("existingUser"));
    }

    @Test
    @WithMockUser
    public void testGetUsernameDoesNotExist() throws Exception {
        // Given
        when(authService.checkUsernameExists(anyString())).thenReturn(false);

        // When & Then
        mockMvc.perform(get("/auth/username-exists")
                .param("username", "newUser")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(false))
                .andExpect(jsonPath("$.message").value("Username is available"))
                .andExpect(jsonPath("$.username").value("newUser"));
    }
    
    @Test
    @WithMockUser
    public void testGetUsernameEmptyParam() throws Exception {
        // When & Then
        mockMvc.perform(get("/auth/username-exists")
                .param("username", "")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isBadRequest());
    }
}