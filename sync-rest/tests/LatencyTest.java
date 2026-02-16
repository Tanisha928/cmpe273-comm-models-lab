import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
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
    //   java LatencyTest 200 http://localhost:8081/order "" latency_report.md
    // Optional 4th arg: path to write markdown report (e.g. ../latency_report.md)
    public static void main(String[] args) throws Exception {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 200;
        String url = args.length > 1 ? args[1] : "http://localhost:8081/order";
        String body = args.length > 2 && args[2] != null && !args[2].isEmpty()
                ? args[2]
                : "{\"user_id\":\"u1\",\"item_id\":\"burrito\",\"qty\":1}";
        String reportPath = args.length > 3 ? args[3] : null;
        String sectionTitle = args.length > 4 ? args[4] : null;  // If set, append this section to report
        String htmlReportPath = args.length > 5 ? args[5] : null;  // If set, write HTML report with charts

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

        double mean = latencies.isEmpty() ? 0 : latencies.stream().mapToLong(x -> x).average().orElse(0);
        long p50 = percentile(latencies, 0.50);
        long p95 = percentile(latencies, 0.95);
        long p99 = percentile(latencies, 0.99);

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

        if (reportPath != null && !reportPath.isEmpty()) {
            String markdown = buildMarkdownReport("Latency Test", url, n, success, exceptions, statusCounts, mean, p50, p95, p99);
            Path outPath = Path.of(reportPath);
            if (outPath.getParent() != null) {
                Files.createDirectories(outPath.getParent());
            }
            if (sectionTitle != null && !sectionTitle.isEmpty()) {
                String section = "\n\n## " + sectionTitle + "\n\n" + markdown;
                if (Files.exists(outPath)) {
                    Files.writeString(outPath, section, StandardOpenOption.APPEND);
                } else {
                    Files.writeString(outPath, "# Sync-REST Latency Report" + section, StandardOpenOption.CREATE);
                }
            } else {
                Files.writeString(outPath, "# Sync-REST Latency Report\n\n" + markdown, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
            System.out.println("Report written to " + reportPath);
        }

        if (htmlReportPath != null && !htmlReportPath.isEmpty()) {
            Path outPath = Path.of(htmlReportPath);
            if (outPath.getParent() != null) {
                Files.createDirectories(outPath.getParent());
            }
            String html = buildHtmlReport(url, n, success, exceptions, statusCounts, mean, p50, p95, p99, latencies, sectionTitle != null ? sectionTitle : "Latency Test");
            Files.writeString(outPath, html, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            System.out.println("HTML report written to " + htmlReportPath);
        }
    }

    private static long percentile(List<Long> sorted, double p) {
        if (sorted == null || sorted.isEmpty()) return 0;
        int idx = (int) Math.ceil(p * sorted.size()) - 1;
        if (idx < 0) idx = 0;
        return sorted.get(idx);
    }

    private static String buildMarkdownReport(String title, String url, int n, int success, int exceptions,
            Map<Integer, Integer> statusCounts, double mean, long p50, long p95, long p99) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(title).append("\n\n");
        sb.append("**Generated:** ").append(Instant.now().toString()).append("\n\n");
        sb.append("| Metric | Value |\n");
        sb.append("|--------|-------|\n");
        sb.append("| URL | ").append(url).append(" |\n");
        sb.append("| N (requests) | ").append(n).append(" |\n");
        sb.append("| Success | ").append(success).append(" (").append(String.format("%.2f", 100.0 * success / n)).append("%) |\n");
        sb.append("| Exceptions | ").append(exceptions).append(" |\n");
        sb.append("| Status counts | ").append(statusCounts).append(" |\n");
        sb.append("| **Avg latency (ms)** | **").append(String.format("%.2f", mean)).append("** |\n");
        sb.append("| **p50 (ms)** | **").append(p50).append("** |\n");
        sb.append("| **p95 (ms)** | **").append(p95).append("** |\n");
        sb.append("| **p99 (ms)** | **").append(p99).append("** |\n");
        return sb.toString();
    }

    private static String buildHtmlReport(String url, int n, int success, int exceptions,
            Map<Integer, Integer> statusCounts, double mean, long p50, long p95, long p99,
            List<Long> latencies, String title) {
        // Bucket latencies for histogram: 0-50, 50-100, 100-200, 200-500, 500-1000, 1000+
        int[] buckets = new int[6];
        String[] labels = { "0-50ms", "50-100ms", "100-200ms", "200-500ms", "500-1000ms", "1000ms+" };
        for (long ms : latencies) {
            if (ms < 50) buckets[0]++;
            else if (ms < 100) buckets[1]++;
            else if (ms < 200) buckets[2]++;
            else if (ms < 500) buckets[3]++;
            else if (ms < 1000) buckets[4]++;
            else buckets[5]++;
        }
        StringBuilder data = new StringBuilder();
        data.append("[");
        for (int i = 0; i < labels.length; i++) {
            if (i > 0) data.append(",");
            data.append(buckets[i]);
        }
        data.append("]");

        String statusStr = statusCounts.toString().replace("\"", "\\\"");
        String json = String.format(
            "var reportData = { \"title\": \"%s\", \"url\": \"%s\", \"n\": %d, \"success\": %d, \"exceptions\": %d, \"mean\": %.2f, \"p50\": %d, \"p95\": %d, \"p99\": %d, \"statusCounts\": \"%s\", \"histogram\": %s, \"labels\": [\"0-50ms\",\"50-100ms\",\"100-200ms\",\"200-500ms\",\"500-1000ms\",\"1000ms+\"] };",
            title.replace("\\", "\\\\").replace("\"", "\\\""),
            url.replace("\\", "\\\\").replace("\"", "\\\""),
            n, success, exceptions, mean, p50, p95, p99,
            statusStr,
            data.toString()
        );

        return "<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"UTF-8\">\n<title>Sync-REST Latency Report</title>\n"
            + "<script src=\"https://cdn.jsdelivr.net/npm/chart.js\"></script>\n"
            + "<style>body{font-family:sans-serif;max-width:900px;margin:2em auto;padding:0 1em;} h1{color:#333;} .stats{display:grid;grid-template-columns:repeat(4,1fr);gap:1em;margin:1em 0;} .stat{background:#f0f4f8;padding:1em;border-radius:8px;text-align:center;} .stat span{display:block;font-size:1.5em;font-weight:bold;color:#2563eb;}</style>\n</head>\n<body>\n"
            + "<h1>Sync-REST Latency Report</h1>\n<p><strong>" + title + "</strong> &middot; Generated: " + Instant.now() + "</p>\n"
            + "<div class=\"stats\">\n"
            + "<div class=\"stat\"><span id=\"mean\">-</span>Avg (ms)</div>\n"
            + "<div class=\"stat\"><span id=\"p50\">-</span>p50 (ms)</div>\n"
            + "<div class=\"stat\"><span id=\"p95\">-</span>p95 (ms)</div>\n"
            + "<div class=\"stat\"><span id=\"p99\">-</span>p99 (ms)</div>\n"
            + "</div>\n"
            + "<p>N=" + n + " &middot; Success=" + success + " (" + String.format("%.2f", 100.0 * success / n) + "%) &middot; Exceptions=" + exceptions + " &middot; Status counts: " + statusCounts + "</p>\n"
            + "<div style=\"height:280px;\"><canvas id=\"percentileChart\"></canvas></div>\n"
            + "<div style=\"height:280px;\"><canvas id=\"histogramChart\"></canvas></div>\n"
            + "<script>\n" + json + "\n"
            + "document.getElementById('mean').textContent = reportData.mean.toFixed(2);\n"
            + "document.getElementById('p50').textContent = reportData.p50;\n"
            + "document.getElementById('p95').textContent = reportData.p95;\n"
            + "document.getElementById('p99').textContent = reportData.p99;\n"
            + "new Chart(document.getElementById('percentileChart'), { type: 'bar', data: { labels: ['Avg', 'p50', 'p95', 'p99'], datasets: [{ label: 'Latency (ms)', data: [reportData.mean, reportData.p50, reportData.p95, reportData.p99], backgroundColor: ['#3b82f6','#10b981','#f59e0b','#ef4444'] }] }, options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true, title: { display: true, text: 'ms' } } } } });\n"
            + "new Chart(document.getElementById('histogramChart'), { type: 'bar', data: { labels: reportData.labels, datasets: [{ label: 'Requests', data: reportData.histogram, backgroundColor: '#6366f1' }] }, options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true, title: { display: true, text: 'Count' } } } } });\n"
            + "</script>\n</body>\n</html>";
    }
}
