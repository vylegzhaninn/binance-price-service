package com.example.price.client;

import com.example.price.client.dto.BinanceAvgPrice;
import com.example.price.exception.BinanceApiException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Thin HTTP client for the Binance Spot API. Any failure (4xx/5xx, rate limit
 * codes 429/418, network or parsing problems) is translated into a single
 * {@link BinanceApiException} so callers have one failure type to handle.
 */
@Component
public class BinanceClient {

    private static final String AVG_PRICE_PATH = "/api/v3/avgPrice";
    private static final int MAX_ERROR_BODY_LENGTH = 500;

    private final RestClient restClient;

    public BinanceClient(RestClient binanceRestClient) {
        this.restClient = binanceRestClient;
    }

    public BinanceAvgPrice fetchAveragePrice(String symbol) {
        try {
            BinanceAvgPrice response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(AVG_PRICE_PATH)
                            .queryParam("symbol", symbol)
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, serverResponse) -> {
                        throw new BinanceApiException(
                                errorMessage(symbol, serverResponse.getStatusCode(), serverResponse.getBody()));
                    })
                    .body(BinanceAvgPrice.class);
            if (response == null) {
                throw new BinanceApiException("Empty response body from " + AVG_PRICE_PATH + " for symbol " + symbol);
            }
            return response;
        } catch (ResourceAccessException e) {
            throw new BinanceApiException("I/O error calling Binance " + AVG_PRICE_PATH + " for symbol " + symbol, e);
        } catch (RestClientException e) {
            throw new BinanceApiException("Failed to read Binance " + AVG_PRICE_PATH + " response for symbol " + symbol, e);
        }
    }

    private static String errorMessage(String symbol, HttpStatusCode status, InputStream body) {
        String bodyText;
        try (body) {
            bodyText = new String(body.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            bodyText = "<unreadable body: " + e.getMessage() + ">";
        }
        if (bodyText.length() > MAX_ERROR_BODY_LENGTH) {
            bodyText = bodyText.substring(0, MAX_ERROR_BODY_LENGTH);
        }
        return "Binance API error for symbol %s: HTTP %d, body: %s".formatted(symbol, status.value(), bodyText);
    }
}
