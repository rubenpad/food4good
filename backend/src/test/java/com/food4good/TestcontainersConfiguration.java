package com.food4good;

import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return newPostgres();
    }

    /** In memory and without syncing to disk: tests don't need to survive a crash, only to be fast. */
    public static PostgreSQLContainer newPostgres() {
        var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
        postgres.withTmpFs(Map.of("/var/lib/postgresql/data", "rw"));
        postgres.withCommand("postgres", "-c", "fsync=off", "-c", "synchronous_commit=off", "-c", "full_page_writes=off");
        return postgres;
    }
}
