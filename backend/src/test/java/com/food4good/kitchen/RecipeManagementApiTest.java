package com.food4good.kitchen;

import static org.assertj.core.api.Assertions.assertThat;

import com.food4good.ApiTest;
import com.food4good.Food4GoodApplication;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.client.RestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RecipeManagementApiTest extends ApiTest {

    @Autowired
    private PostgreSQLContainer postgres;

    private static Map<String, Object> recipe(String name, int seconds, Map<?, ?>... ingredients) {
        return Map.of("name", name, "preparationTimeSeconds", seconds, "ingredients", List.of(ingredients));
    }

    private static Map<String, Object> item(String ingredient, int quantity) {
        return Map.of("ingredient", ingredient, "quantity", quantity);
    }

    private String create(String name) {
        var response = send(HttpMethod.POST, "/recipes", recipe(name, 40, item("rice", 1)));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return JsonPath.read(response.getBody(), "$.id");
    }

    private static String detail(ResponseEntity<String> response) {
        return JsonPath.read(response.getBody(), "$.detail");
    }

    private List<Map<String, Object>> recipesNamed(String name) {
        return getJson("/recipes").read("$[?(@.name == '" + name + "')]");
    }

    @Test
    void createsARecipeAndListsIt() {
        var response = send(HttpMethod.POST, "/recipes",
                recipe("Plain Rice", 45, item("rice", 3), item("onion", 1)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = JsonPath.read(response.getBody(), "$.id");
        assertThat(UUID.fromString(id)).isNotNull();
        assertThat(response.getHeaders().getLocation()).hasPath("/api/recipes/" + id);
        assertThat(recipesNamed("Plain Rice")).containsExactly(Map.of(
                "id", id,
                "name", "Plain Rice",
                "preparationTimeSeconds", 45,
                "ingredients", List.of(item("onion", 1), item("rice", 3))));
    }

    @Test
    void editReplacesTheWholeRecipeIncludingRemovedIngredients() {
        var created = send(HttpMethod.POST, "/recipes",
                recipe("Before Edit", 45, item("rice", 3), item("onion", 1)));
        String id = JsonPath.read(created.getBody(), "$.id");

        var response = send(HttpMethod.PUT, "/recipes/" + id, recipe("After Edit", 70, item("meat", 2)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(recipesNamed("Before Edit")).isEmpty();
        assertThat(recipesNamed("After Edit")).containsExactly(Map.of(
                "id", id,
                "name", "After Edit",
                "preparationTimeSeconds", 70,
                "ingredients", List.of(item("meat", 2))));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"name\":\"  \",\"preparationTimeSeconds\":10,\"ingredients\":[{\"ingredient\":\"rice\",\"quantity\":1}]}",
            "{\"preparationTimeSeconds\":10,\"ingredients\":[{\"ingredient\":\"rice\",\"quantity\":1}]}",
            "{\"name\":\"Invalid\",\"preparationTimeSeconds\":10,\"ingredients\":[]}",
            "{\"name\":\"Invalid\",\"preparationTimeSeconds\":10}",
            "{\"name\":\"Invalid\",\"preparationTimeSeconds\":10,\"ingredients\":[{\"ingredient\":\"pizza\",\"quantity\":1}]}",
            "{\"name\":\"Invalid\",\"preparationTimeSeconds\":10,\"ingredients\":[{\"quantity\":1}]}",
            "{\"name\":\"Invalid\",\"preparationTimeSeconds\":10,\"ingredients\":[{\"ingredient\":\"rice\",\"quantity\":0}]}",
            "{\"name\":\"Invalid\",\"preparationTimeSeconds\":10,\"ingredients\":[{\"ingredient\":\"rice\",\"quantity\":-1}]}",
            "{\"name\":\"Invalid\",\"preparationTimeSeconds\":10,\"ingredients\":[{\"ingredient\":\"rice\",\"quantity\":1.5}]}",
            "{\"name\":\"Invalid\",\"preparationTimeSeconds\":10,\"ingredients\":[{\"ingredient\":\"rice\"}]}",
            "{\"name\":\"Invalid\",\"preparationTimeSeconds\":10,\"ingredients\":[{\"ingredient\":\"rice\",\"quantity\":1},{\"ingredient\":\"rice\",\"quantity\":2}]}",
            "{\"name\":\"Invalid\",\"preparationTimeSeconds\":0,\"ingredients\":[{\"ingredient\":\"rice\",\"quantity\":1}]}",
            "{\"name\":\"Invalid\",\"preparationTimeSeconds\":-5,\"ingredients\":[{\"ingredient\":\"rice\",\"quantity\":1}]}",
            "{\"name\":\"Invalid\",\"preparationTimeSeconds\":1.5,\"ingredients\":[{\"ingredient\":\"rice\",\"quantity\":1}]}",
            "{\"name\":\"Invalid\",\"ingredients\":[{\"ingredient\":\"rice\",\"quantity\":1}]}",
            "{\"name\":\"Invalid\",\"preparationTimeSeconds\":10,\"ingredients\":[null]}",
            "not json",
    })
    void refusesInvalidRecipesWithAClearMessage(String body) {
        var id = create("Valid " + UUID.randomUUID());

        for (var response : List.of(
                send(HttpMethod.POST, "/recipes", body),
                send(HttpMethod.PUT, "/recipes/" + id, body))) {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(detail(response)).isNotBlank();
        }
        assertThat(recipesNamed("Invalid")).isEmpty();
    }

    @Test
    void aRowWithNoIngredientChosenGetsAMessageTheManagerCanActOn() {
        var response = send(HttpMethod.POST, "/recipes",
                "{\"name\":\"Invalid\",\"preparationTimeSeconds\":10,\"ingredients\":[{\"quantity\":1}]}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detail(response)).isEqualTo("Choose an Ingredient for every row of the Recipe");
    }

    @Test
    void refusesADuplicateNameOnCreate() {
        create("Taken Name");

        var response = send(HttpMethod.POST, "/recipes", recipe("Taken Name", 99, item("meat", 1)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(detail(response)).contains("Taken Name");
        assertThat(recipesNamed("Taken Name")).hasSize(1).first()
                .satisfies(recipe -> assertThat(recipe).containsEntry("preparationTimeSeconds", 40));
    }

    @Test
    void refusesADuplicateNameOnEditAndChangesNothing() {
        create("Already Here");
        var id = create("Renamed Later");

        var response = send(HttpMethod.PUT, "/recipes/" + id, recipe("Already Here", 99, item("meat", 1)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(recipesNamed("Renamed Later")).hasSize(1).first().satisfies(recipe -> {
            assertThat(recipe).containsEntry("preparationTimeSeconds", 40);
            assertThat(recipe).containsEntry("ingredients", List.of(item("rice", 1)));
        });
    }

    @Test
    void editKeepingTheSameNameIsNotADuplicate() {
        var id = create("Same Name");

        var response = send(HttpMethod.PUT, "/recipes/" + id, recipe("Same Name", 41, item("rice", 2)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void namesIgnoreSurroundingWhitespace() {
        create("Trimmed");

        var response = send(HttpMethod.POST, "/recipes", recipe("  Trimmed ", 40, item("rice", 1)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void editingAnUnknownRecipeIsNotFound() {
        var response = send(HttpMethod.PUT, "/recipes/" + UUID.randomUUID(), recipe("Nobody", 40, item("rice", 1)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(detail(response)).isNotBlank();
    }

    @Test
    void editsSurviveARestartAndTheStartingRecipesAreNotReinserted() {
        var edited = (Map<String, Object>) getJson("/recipes").read("$[?(@.name == 'Tomato Rice')]", List.class).getFirst();
        send(HttpMethod.PUT, "/recipes/" + edited.get("id"), recipe("Tomato Rice Deluxe", 66, item("rice", 4)));
        var created = create("Survives Restart");

        try (var restarted = new SpringApplicationBuilder(Food4GoodApplication.class).run(
                "--server.port=0",
                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword())) {
            var port = restarted.getEnvironment().getProperty("local.server.port");
            var body = RestClient.create("http://localhost:" + port + "/api/recipes").get().retrieve().body(String.class);

            List<String> names = JsonPath.read(body, "$[*].name");
            assertThat(names).contains("Tomato Rice Deluxe", "Survives Restart").doesNotContain("Tomato Rice");
            List<String> ids = JsonPath.read(body, "$[*].id");
            assertThat(ids).contains(created);
        }
    }

    @Test
    void helperMakesEveryRecipeTheSame() {
        givenEveryRecipeIs(Map.of("cheese", 2), 5);

        List<Map<String, Object>> recipes = getJson("/recipes").read("$");
        assertThat(recipes).isNotEmpty().allSatisfy(recipe -> {
            assertThat(recipe).containsEntry("preparationTimeSeconds", 5);
            assertThat(recipe).containsEntry("ingredients", List.of(item("cheese", 2)));
        });
    }
}
