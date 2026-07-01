package com.glory.spotflow_wallet.spotflow;

import com.glory.spotflow_wallet.spotflow.dto.CreateDynamicAccountRequest;
import com.glory.spotflow_wallet.spotflow.dto.CreateDynamicAccountResponse;
import com.glory.spotflow_wallet.spotflow.dto.CreateTransferRequest;
import com.glory.spotflow_wallet.spotflow.dto.TransferDetailsResponse;
import com.glory.spotflow_wallet.spotflow.exception.SpotflowApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Real network implementation. Active when spotflow.mock=false.
 *
 * All Spotflow-specific HTTP concerns (base URL, bearer auth, error mapping) are
 * contained here - nothing else in the app touches RestClient or knows the URL paths.
 */
public class HttpSpotflowClient implements SpotflowClient {

    private static final Logger log = LoggerFactory.getLogger(HttpSpotflowClient.class);

    private final RestClient restClient;

    public HttpSpotflowClient(SpotflowProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.getSecretKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public CreateDynamicAccountResponse createDynamicAccount(CreateDynamicAccountRequest request) {
        try {
            return restClient.post()
                    .uri("/virtual-accounts/temporary")
                    .body(request)
                    .retrieve()
                    .body(CreateDynamicAccountResponse.class);
        } catch (RestClientResponseException ex) {
            log.error("Spotflow createDynamicAccount failed ({}): {}",
                    ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw new SpotflowApiException(
                    "Failed to create dynamic account: " + ex.getResponseBodyAsString(),
                    ex.getStatusCode().value());
        }
    }

    @Override
    public TransferDetailsResponse createTransfer(CreateTransferRequest request) {
        try {
            return restClient.post()
                    .uri("/transfers")
                    .body(request)
                    .retrieve()
                    .body(TransferDetailsResponse.class);
        } catch (RestClientResponseException ex) {
            log.error("Spotflow createTransfer failed ({}): {}",
                    ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw new SpotflowApiException(
                    "Failed to create transfer: " + ex.getResponseBodyAsString(),
                    ex.getStatusCode().value());
        }
    }

    @Override
    public TransferDetailsResponse getTransferByReference(String reference) {
        try {
            return restClient.get()
                    .uri("/transfers/reference/{reference}", reference)
                    .retrieve()
                    .body(TransferDetailsResponse.class);
        } catch (RestClientResponseException ex) {
            log.error("Spotflow getTransferByReference({}) failed ({}): {}",
                    reference, ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw new SpotflowApiException(
                    "Failed to fetch transfer by reference: " + ex.getResponseBodyAsString(),
                    ex.getStatusCode().value());
        }
    }
}
