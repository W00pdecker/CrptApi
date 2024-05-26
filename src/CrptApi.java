import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CrptApi {

    private final HttpClient httpClient;
    private final String apiUrl = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final Semaphore semaphore;
    private final ScheduledExecutorService scheduler;
    private final ObjectMapper objectMapper;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.httpClient = HttpClient.newHttpClient();
        this.semaphore = new Semaphore(requestLimit);
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.objectMapper = new ObjectMapper();

        long delay = timeUnit.toMillis(1);

        scheduler.scheduleAtFixedRate(() -> {
            semaphore.release(requestLimit - semaphore.availablePermits());
        }, delay, delay, TimeUnit.MILLISECONDS); //через заданные интервалы освобождаем пермиты семафора до максимума
    }

    public void createDocument(Document document, String signature) throws IOException {
        try {
            semaphore.acquire(); // получаем пермит перед тем как отправить реквест

            String jsonDocument = objectMapper.writeValueAsString(document);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonDocument))
                    .build();   //создаем реквест на заданный URL

            HttpResponse<String> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new IOException("Failed to create document: " + response.body());
                }
            } catch (InterruptedException | IOException e) {
                throw new IOException("Error sending request: " + e.getMessage(), e);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Thread was interrupted: " + e.getMessage(), e);
        }
    }

    public static class Document {
        @JsonProperty("description")
        public Description description;
        @JsonProperty("doc_id")
        public String docId;
        @JsonProperty("doc_status")
        public String docStatus;
        @JsonProperty("doc_type")
        public String docType;
        @JsonProperty("importRequest")
        public boolean importRequest;
        @JsonProperty("owner_inn")
        public String ownerInn;
        @JsonProperty("participant_inn")
        public String participantInn;
        @JsonProperty("producer_inn")
        public String producerInn;
        @JsonProperty("production_date")
        public String productionDate;
        @JsonProperty("production_type")
        public String productionType;
        @JsonProperty("products")
        public ArrayList<Product> products = new ArrayList<>(); // инициализируется здесь, чтобы избежать NullPointerException
        @JsonProperty("reg_date")
        public String regDate;
        @JsonProperty("reg_number")
        public String regNumber;
    }

    public static class Description {
        @JsonProperty("participantInn")
        public String participantInn;
    }

    public static class Product {
        @JsonProperty("certificate_document")
        public String certificateDocument;
        @JsonProperty("certificate_document_date")
        public String certificateDocumentDate;
        @JsonProperty("certificate_document_number")
        public String certificateDocumentNumber;
        @JsonProperty("owner_inn")
        public String ownerInn;
        @JsonProperty("producer_inn")
        public String producerInn;
        @JsonProperty("production_date")
        public String productionDate;
        @JsonProperty("tnved_code")
        public String tnvedCode;
        @JsonProperty("uit_code")
        public String uitCode;
        @JsonProperty("uitu_code")
        public String uituCode;

    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        CrptApi api = new CrptApi(TimeUnit.MINUTES, 10);

        Document doc = new Document();
        doc.docId = "12345";
        doc.docStatus = "ACTIVE";
        doc.docType = "LP_INTRODUCE_GOODS";
        doc.importRequest = true;
        doc.ownerInn = "owner_inn_value";
        doc.participantInn = "participant_inn_value";
        doc.producerInn = "producer_inn_value";
        doc.productionDate = "2020-01-23";
        doc.productionType = "type_value";
        Product product = new Product();
        product.certificateDocument = "doc";
        product.certificateDocumentDate = "2020-01-23";
        product.certificateDocumentNumber = "123";
        product.ownerInn = "owner_inn";
        product.producerInn = "producer_inn";
        product.productionDate = "2020-01-23";
        product.tnvedCode = "tnved";
        product.uitCode = "uit";
        product.uituCode = "uitu";
        doc.products.add(product);
        doc.regDate = "2020-01-23";
        doc.regNumber = "reg_number";

        try {
            api.createDocument(doc, "signature_value");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            api.shutdown();
        }
    }
}
