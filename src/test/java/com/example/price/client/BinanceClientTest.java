package com.example.price.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.example.price.client.dto.BinanceAvgPrice;
import java.io.IOException;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class BinanceClientTest {

    private static final String AVG_PRICE_URL = "https://api.binance.com/api/v3/avgPrice?symbol=BNBUSDT";

    private MockRestServiceServer server;
    private BinanceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.binance.com");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new BinanceClient(builder.build());
    }

    @Test
    @DisplayName("successful response is parsed with the exact price scale")
    void fetchAveragePrice() {
        server.expect(requestTo(AVG_PRICE_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"mins\":5,\"price\":\"612.34567890\",\"closeTime\":1694061154503}",
                        MediaType.APPLICATION_JSON));

        BinanceAvgPrice result = client.fetchAveragePrice("BNBUSDT");

        assertThat(result.priceValue()).isEqualByComparingTo("612.34567890");
        server.verify();
    }

    @Test
    @DisplayName("rate limit 429 becomes BinanceApiException with status and body")
    void rateLimitError() {
        server.expect(requestTo(AVG_PRICE_URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":-1003,\"msg\":\"Way too much request weight used\"}"));

        assertThatThrownBy(() -> client.fetchAveragePrice("BNBUSDT"))
                .isInstanceOf(BinanceApiException.class)
                .hasMessageContaining("429")
                .hasMessageContaining("-1003");
    }

    @Test
    @DisplayName("server errors are translated the same way")
    void serverError() {
        server.expect(requestTo(AVG_PRICE_URL))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.fetchAveragePrice("BNBUSDT"))
                .isInstanceOf(BinanceApiException.class)
                .hasMessageContaining("500");
    }

    @Test
    @DisplayName("network failures are translated to BinanceApiException")
    void ioError() {
        server.expect(requestTo(AVG_PRICE_URL))
                .andRespond(withException(new IOException("connection reset")));

        assertThatThrownBy(() -> client.fetchAveragePrice("BNBUSDT"))
                .isInstanceOf(BinanceApiException.class)
                .hasRootCauseMessage("connection reset");
    }

    @Test
    @DisplayName("unparseable body is translated to BinanceApiException")
    void malformedBody() {
        server.expect(requestTo(AVG_PRICE_URL))
                .andRespond(withSuccess("<html>proxy error</html>", MediaType.TEXT_HTML));

        assertThatThrownBy(() -> client.fetchAveragePrice("BNBUSDT"))
                .isInstanceOf(BinanceApiException.class);
    }

    @Test
    @DisplayName("empty body is translated to BinanceApiException")
    void emptyBody() {
        server.expect(requestTo(AVG_PRICE_URL))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchAveragePrice("BNBUSDT"))
                .isInstanceOf(BinanceApiException.class)
                .hasMessageContaining("Empty response body");
    }
}
