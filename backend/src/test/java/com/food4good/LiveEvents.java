package com.food4good;

import com.jayway.jsonpath.JsonPath;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** A test client for {@code GET /api/events}: collects every batch it receives. */
public final class LiveEvents implements AutoCloseable {

    /** One SSE message, with when it arrived. */
    public record Batch(String type, long id, String data, long receivedAtMillis) {
    }

    private final List<Batch> batches = new CopyOnWriteArrayList<>();
    private final InputStream stream;
    private final Thread reader;

    private LiveEvents(InputStream stream) {
        this.stream = stream;
        this.reader = Thread.ofVirtual().start(this::read);
    }

    public static LiveEvents subscribe(String baseUrl) throws IOException, InterruptedException {
        var response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/events")).header("Accept", "text/event-stream").build(),
                HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("GET /events returned " + response.statusCode());
        }
        return new LiveEvents(response.body());
    }

    private void read() {
        try (var lines = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String type = null;
            long id = -1;
            var data = new StringBuilder();
            for (var line = lines.readLine(); line != null; line = lines.readLine()) {
                if (line.startsWith("event:")) {
                    type = line.substring(6).strip();
                } else if (line.startsWith("id:")) {
                    id = Long.parseLong(line.substring(3).strip());
                } else if (line.startsWith("data:")) {
                    data.append(line.substring(5).strip());
                } else if (line.isEmpty() && type != null) {
                    batches.add(new Batch(type, id, data.toString(), System.currentTimeMillis()));
                    type = null;
                    data.setLength(0);
                }
            }
        } catch (IOException | RuntimeException e) {
            // The connection closed, which ends the stream.
        }
    }

    public List<Batch> batches(String type) {
        return batches.stream().filter(batch -> batch.type().equals(type)).toList();
    }

    public List<Batch> allBatches() {
        return List.copyOf(batches);
    }

    /** Every Order this client was told about, oldest news first. */
    public List<Map<String, Object>> orders() {
        var orders = new ArrayList<Map<String, Object>>();
        batches("orders").forEach(batch -> orders.addAll(JsonPath.<List<Map<String, Object>>>read(batch.data(), "$")));
        return orders;
    }

    /** Every Purchase this client was told about, in the order it heard of them. */
    public List<Map<String, Object>> purchases() {
        var purchases = new ArrayList<Map<String, Object>>();
        batches("purchases").forEach(batch -> purchases.addAll(JsonPath.<List<Map<String, Object>>>read(batch.data(), "$")));
        return purchases;
    }

    /** What the latest {@code storage} batch says about one Ingredient, as {@code [onHand, reserved, available]}. */
    public List<Integer> latestStockOf(String ingredient) {
        var all = batches("storage");
        if (all.isEmpty()) {
            return List.of();
        }
        List<Map<String, Integer>> rows =
                JsonPath.read(all.getLast().data(), "$[?(@.ingredient == '" + ingredient + "')]");
        var row = rows.getFirst();
        return List.of(row.get("onHand"), row.get("reserved"), row.get("available"));
    }

    @Override
    public void close() throws IOException {
        stream.close();
        reader.interrupt();
    }
}
