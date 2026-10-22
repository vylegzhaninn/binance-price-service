package com.example.price;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.price.domain.PriceRecord;
import com.example.price.repository.PriceRecordRepository;
import com.example.price.scheduler.PriceFetchScheduler;
import com.example.price.service.PriceIngestionService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/**
 * End-to-end test: real PostgreSQL (Testcontainers), Binance replaced by WireMock,
 * REST endpoints called over HTTP the same way a client would.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PriceApiIntegrationTest {

    private static final String SYMBOL = "BNBUSDT";
    private static final Instant BASE = Instant.parse("2026-10-01T10:00:00Z");

    /** Parses numbers as BigDecimal so price assertions keep exact decimal values. */
    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);

    private static final WireMockServer binanceMock = new WireMockServer(
            WireMockConfiguration.options().dynamicPort());

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void binanceProperties(DynamicPropertyRegistry registry) {
        if (!binanceMock.isRunning()) {
            binanceMock.start();
        }
        registry.add("binance.base-url", binanceMock::baseUrl);
    }

    @AfterAll
    static void stopBinanceMock() {
        binanceMock.stop();
    }

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private PriceRecordRepository repository;
    @Autowired
    private PriceIngestionService ingestionService;
    @Autowired
    private PriceFetchScheduler scheduler;

    @BeforeEach
    void cleanState() {
        repository.deleteAll();
        binanceMock.resetAll();
    }

    @Test
    @DisplayName("a poll stores the Binance price and /latest returns it in ISO-8601 UTC format")
    void latestReturnsPolledPrice() throws Exception {
        stubAvgPrice("612.34567890");

        ingestionService.fetchAndSaveAveragePrice();

        ResponseEntity<String> response = rest.getForEntity("/api/v1/prices/latest", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = JSON.readTree(response.getBody());
        assertThat(body.get("symbol").asText()).isEqualTo(SYMBOL);
        assertThat(body.get("price").decimalValue()).isEqualByComparingTo(new BigDecimal("612.34567890"));
        assertThat(body.get("fetchedAt").asText()).matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$");
    }

    @Test
    @DisplayName("/average returns the exact mean of stored records in the period")
    void averageOverPeriod() throws Exception {
        seedRecords(
                price("100", BASE),
                price("200", BASE.plus(10, ChronoUnit.MINUTES)),
                price("300", BASE.plus(20, ChronoUnit.MINUTES)));

        ResponseEntity<String> response = rest.getForEntity(
                "/api/v1/prices/average?from={from}&to={to}", String.class,
                BASE.toString(), BASE.plus(30, ChronoUnit.MINUTES).toString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(JSON.readTree(response.getBody()).get("averagePrice").decimalValue())
                .isEqualByComparingTo(new BigDecimal("200"));
    }

    @Test
    @DisplayName("/prices pages through records of the period, newest first")
    void historyPagination() throws Exception {
        for (int i = 0; i < 5; i++) {
            repository.save(price("100." + i, BASE.plus(i, ChronoUnit.MINUTES)));
        }

        ResponseEntity<String> firstPage = rest.getForEntity(
                "/api/v1/prices?from={from}&to={to}&page=0&size=2", String.class,
                BASE.toString(), BASE.plus(1, ChronoUnit.HOURS).toString());
        assertThat(firstPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode page = JSON.readTree(firstPage.getBody());
        assertThat(page.get("items").size()).isEqualTo(2);
        assertThat(page.get("page").asInt()).isZero();
        assertThat(page.get("size").asInt()).isEqualTo(2);
        assertThat(page.get("totalElements").asLong()).isEqualTo(5);
        assertThat(page.get("hasNext").asBoolean()).isTrue();
        // newest first
        assertThat(page.get("items").get(0).get("fetchedAt").asText())
                .isEqualTo(BASE.plus(4, ChronoUnit.MINUTES).toString());

        ResponseEntity<String> lastPage = rest.getForEntity(
                "/api/v1/prices?from={from}&to={to}&page=2&size=2", String.class,
                BASE.toString(), BASE.plus(1, ChronoUnit.HOURS).toString());
        JsonNode tail = JSON.readTree(lastPage.getBody());
        assertThat(tail.get("items").size()).isEqualTo(1);
        assertThat(tail.get("hasNext").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("/trend reports UPWARD for a rising period")
    void trendUpward() throws Exception {
        seedRecords(price("100", BASE), price("150", BASE.plus(1, ChronoUnit.HOURS)));

        ResponseEntity<String> response = rest.getForEntity(
                "/api/v1/prices/trend?from={from}&to={to}", String.class,
                BASE.toString(), BASE.plus(2, ChronoUnit.HOURS).toString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = JSON.readTree(response.getBody());
        assertThat(body.get("symbol").asText()).isEqualTo(SYMBOL);
        assertThat(body.get("start_date").asText()).isEqualTo("2026-10-01");
        assertThat(body.get("end_date").asText()).isEqualTo("2026-10-01");
        assertThat(body.get("trend").asText()).isEqualTo("UPWARD");
    }

    @Test
    @DisplayName("/trend reports FLAT when the change stays inside the noise band")
    void trendFlat() throws Exception {
        seedRecords(price("100", BASE), price("100.05", BASE.plus(1, ChronoUnit.HOURS)));

        ResponseEntity<String> response = rest.getForEntity(
                "/api/v1/prices/trend?from={from}&to={to}", String.class,
                BASE.toString(), BASE.plus(2, ChronoUnit.HOURS).toString());

        assertThat(JSON.readTree(response.getBody()).get("trend").asText()).isEqualTo("FLAT");
    }

    @Test
    @DisplayName("a failed Binance call is skipped: no exception, no data loss or duplication")
    void failedPollKeepsDataIntact() {
        binanceMock.stubFor(get(urlPathEqualTo("/api/v3/avgPrice"))
                .willReturn(aResponse().withStatus(429)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":-1003,\"msg\":\"Too much request weight used\"}")));
        seedRecords(price("100", BASE));
        long countBefore = repository.count();

        scheduler.fetchAndStorePrice(); // must swallow the failure

        assertThat(repository.count()).isEqualTo(countBefore);

        stubAvgPrice("612.34");
        scheduler.fetchAndStorePrice();

        assertThat(repository.count()).isEqualTo(countBefore + 1);
    }

    @Test
    @DisplayName("no stored data gives a 404 problem detail")
    void latestWithoutDataGives404() throws Exception {
        ResponseEntity<String> response = rest.getForEntity("/api/v1/prices/latest", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(JSON.readTree(response.getBody()).get("status").asInt()).isEqualTo(404);
    }

    @Test
    @DisplayName("inverted period gives a 400 problem detail")
    void invalidPeriodGives400() {
        ResponseEntity<String> response = rest.getForEntity(
                "/api/v1/prices/average?from={from}&to={to}", String.class,
                BASE.plus(1, ChronoUnit.HOURS).toString(), BASE.toString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("unknown path gives 404, not 500")
    void unknownPathGives404() {
        ResponseEntity<String> response = rest.getForEntity("/api/v1/unknown", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("unsupported method gives 405, not 500")
    void wrongMethodGives405() {
        ResponseEntity<String> response = rest.postForEntity("/api/v1/prices/latest", null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    private void stubAvgPrice(String price) {
        binanceMock.stubFor(get(urlPathEqualTo("/api/v3/avgPrice"))
                .willReturn(okJson("{\"mins\":5,\"price\":\"%s\",\"closeTime\":1694061154503}"
                        .formatted(price))));
    }

    private static PriceRecord price(String value, Instant fetchedAt) {
        return new PriceRecord(SYMBOL, new BigDecimal(value), fetchedAt);
    }

    private void seedRecords(PriceRecord... records) {
        repository.saveAll(List.of(records));
    }
}
