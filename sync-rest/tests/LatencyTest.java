import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LatencyTest {

    // Usage:
    //   java LatencyTest 200
    //   java LatencyTest 200 http://localhost:8081/order
    //   java LatencyTest 200 http://localhost:8081/order "{\"user_id\":\"u1\",\"item_id\":\"burrito\",\"qty\":1}"
    public static void main(String[] args) throws Exception {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 200;
        String url = args.length > 1 ? args[1] : "http://localhost:8081/order";
        String body = args.length > 2 ? args[2]
                : "{\"user_id\":\"u1\",\"item_id\":\"burrito\",\"qty\":1}";

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        List<Long> latencies = new ArrayList<>(n);
        int success = 0;
        Map<Integer, Integer> statusCounts = new HashMap<>();
        int exceptions = 0;

        for (int i = 0; i < n; i++) {
            long startNs = System.nanoTime();

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            try {
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

                long endNs = System.nanoTime();
                long ms = (endNs - startNs) / 1_000_000;
                latencies.add(ms);

                int code = resp.statusCode();
                statusCounts.put(code, statusCounts.getOrDefault(code, 0) + 1);

                if (code >= 200 && code < 300) {
                    success++;
                }
            } catch (Exception e) {
                // record exception and continue sending remaining requests
                exceptions++;
                System.out.println("request " + (i + 1) + " failed: " + e.getClass().getSimpleName() + " " + e.getMessage());
            }

            // Optional: print every 50 requests so you know it's running
            if ((i + 1) % 50 == 0) {
                System.out.println("sent " + (i + 1) + "/" + n);
            }
        }

        Collections.sort(latencies);

        double mean = latencies.stream().mapToLong(x -> x).average().orElse(0);
        long p50 = latencies.get((int) Math.ceil(0.50 * latencies.size()) - 1);
        long p95 = latencies.get((int) Math.ceil(0.95 * latencies.size()) - 1);
        long p99 = latencies.get((int) Math.ceil(0.99 * latencies.size()) - 1);

        System.out.println("\n=== Latency Results ===");
        System.out.println("URL=" + url);
        System.out.println("N=" + n);
        System.out.println("success=" + success + " (" + String.format("%.2f", 100.0 * success / n) + "%)");
        System.out.println("exceptions=" + exceptions);
        System.out.println("status_counts=" + statusCounts);
        System.out.println("mean_ms=" + String.format("%.2f", mean));
        System.out.println("p50_ms=" + p50);
        System.out.println("p95_ms=" + p95);
        System.out.println("p99_ms=" + p99);
    }
}
