package com.mullagaliev.aqpbalancer.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StandaloneRuntimeIntegrationTest {

    private static final Path SERVERS_FILE;

    static {
        try {
            SERVERS_FILE = Files.createTempDirectory("aqp-standalone-test")
                    .resolve("connected-servers.json");
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("balancer.servers-file", SERVERS_FILE::toString);
        registry.add("balancer.demo.enabled", () -> "false");
        registry.add("balancer.discovery.enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void startsWithoutDemoDataAndServesHealth() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/servers")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void demoEndpointsAreNotRegistered() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/simulate")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        })
                        .contentType("application/json")
                        .content("{\"totalQueries\":10,\"seed\":1,\"alpha\":2.5}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/stream")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isNotFound());
    }

    @Test
    void readinessIsOutOfServiceWithoutServers() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/actuator/health/readiness"))
                .andExpect(status().isServiceUnavailable());
    }
}
