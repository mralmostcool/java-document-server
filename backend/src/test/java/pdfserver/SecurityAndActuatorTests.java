package pdfserver;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/java_document_server",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.data.redis.password=redispassword123",
        "management.health.redis.enabled=false",
        "management.health.db.enabled=false",
        "management.health.diskspace.enabled=false",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none"
})
@AutoConfigureMockMvc
class SecurityAndActuatorTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void protectedRouteReturns401Json() throws Exception {
        mockMvc.perform(get("/api/v1/documents"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("UNAUTHORIZED")))
                .andExpect(jsonPath("$.path", is("/api/v1/documents")));
    }

    @Test
    void unknownPathReturns404Json() throws Exception {
        mockMvc.perform(get("/nope").with(user("testuser")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("NOT_FOUND")))
                .andExpect(jsonPath("$.path", is("/nope")));
    }

    @Test
    void anonymousActuatorEnvReturns401Or404Never500() throws Exception {
        int statusCode = mockMvc.perform(get("/actuator/env"))
                .andReturn()
                .getResponse()
                .getStatus();
        org.assertj.core.api.Assertions.assertThat(statusCode).isIn(401, 404);
        org.assertj.core.api.Assertions.assertThat(statusCode).isNotEqualTo(500);
    }

    @Test
    void anonymousActuatorHealthReturns200WithoutDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void anonymousActuatorInfoReturns200() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }
}
