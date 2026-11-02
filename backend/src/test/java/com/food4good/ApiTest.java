package com.food4good;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

/**
 * Base for behaviour tests: the whole app against real PostgreSQL, driven and observed only
 * through the public HTTP API. Tests control behaviour through configuration, never test hooks.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
public abstract class ApiTest {

    @LocalServerPort
    private int port;

    protected RestClient api() {
        return RestClient.create("http://localhost:" + port + "/api");
    }

    protected DocumentContext getJson(String path) {
        return JsonPath.parse(api().get().uri(path).retrieve().body(String.class));
    }
}
