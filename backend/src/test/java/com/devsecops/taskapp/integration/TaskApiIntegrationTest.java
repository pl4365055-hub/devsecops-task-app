package com.devsecops.taskapp.integration;

import com.devsecops.taskapp.entity.Task;
import com.devsecops.taskapp.dto.LoginRequest;
import com.devsecops.taskapp.dto.LoginResponse;
import com.devsecops.taskapp.mapper.TaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class TaskApiIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TaskMapper taskMapper;

    @BeforeEach
    void setUp() {
        taskMapper.delete(null);  // 清空表
    }

    @Test
    void createTask_shouldReturn200AndPersist() {
        ResponseEntity<LoginResponse> loginResponse = restTemplate.postForEntity(
            "/api/auth/login",
            new LoginRequest("user", "password"),
            LoginResponse.class
        );

        assertEquals(200, loginResponse.getStatusCode().value());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(loginResponse.getBody().token());

        Task task = new Task();
        task.setTitle("Integration Test");
        task.setStatus("PENDING");

        ResponseEntity<Task> response = restTemplate.postForEntity(
            "/api/tasks", new HttpEntity<>(task, headers), Task.class);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody().getId());

        // 验证数据库中确实存在
        Task saved = taskMapper.selectById(response.getBody().getId());
        assertNotNull(saved);
        assertEquals("Integration Test", saved.getTitle());
    }
}
