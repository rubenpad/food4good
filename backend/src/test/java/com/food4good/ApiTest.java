package com.food4good;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    /** Sends a JSON body, given as a raw string or as anything serializable, and returns any response as is. */
    protected ResponseEntity<String> send(HttpMethod method, String path, Object body) {
        return api().method(method)
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(status -> true, (request, response) -> {
                })
                .toEntity(String.class);
    }

    /** Places Orders and returns the accepted Orders' ids. */
    protected List<String> placeOrders(int quantity) {
        var response = send(HttpMethod.POST, "/orders", Map.of("quantity", quantity));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return JsonPath.read(response.getBody(), "$.orderIds");
    }

    /** The Order as the Order history reports it. */
    protected Map<String, Object> order(String id) {
        List<Map<String, Object>> found = getJson("/orders?limit=200").read("$.items[?(@.id == '" + id + "')]");
        assertThat(found).hasSize(1);
        return found.getFirst();
    }

    protected String statusOf(String orderId) {
        return (String) order(orderId).get("status");
    }

    /** One Ingredient's stock as {@code [onHand, reserved, available]}. */
    protected List<Integer> stockOf(String ingredient) {
        List<Map<String, Object>> stock = getJson("/storage").read("$[?(@.ingredient == '" + ingredient + "')]");
        assertThat(stock).hasSize(1);
        var row = stock.getFirst();
        return List.of((Integer) row.get("onHand"), (Integer) row.get("reserved"), (Integer) row.get("available"));
    }

    protected void assertStockRulesHold() {
        List<Map<String, Integer>> stock = getJson("/storage").read("$");
        assertThat(stock).hasSize(10).allSatisfy(row -> {
            assertThat(row.get("onHand")).isNotNegative();
            assertThat(row.get("reserved")).isNotNegative().isLessThanOrEqualTo(row.get("onHand"));
            assertThat(row.get("available")).isEqualTo(row.get("onHand") - row.get("reserved"));
        });
    }

    /**
     * Edits every Recipe to need the same Ingredients and take the same time, so the random draw
     * doesn't matter. Edits the shared database, so the test class needs {@code @DirtiesContext}.
     */
    protected void givenEveryRecipeIs(Map<String, Integer> ingredients, int preparationTimeSeconds) {
        var items = ingredients.entrySet().stream()
                .map(entry -> Map.of("ingredient", entry.getKey(), "quantity", entry.getValue()))
                .toList();
        List<Map<String, Object>> recipes = getJson("/recipes").read("$");
        recipes.forEach(recipe -> api().put()
                .uri("/recipes/{id}", recipe.get("id"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "name", recipe.get("name"),
                        "preparationTimeSeconds", preparationTimeSeconds,
                        "ingredients", items))
                .retrieve()
                .toBodilessEntity());
    }
}
