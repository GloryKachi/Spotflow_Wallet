package com.glory.spotflow_wallet.spotflow;

import com.glory.spotflow_wallet.spotflow.dto.CreateDynamicAccountRequest;
import com.glory.spotflow_wallet.spotflow.dto.CreateDynamicAccountResponse;
import com.glory.spotflow_wallet.spotflow.dto.CreateTransferRequest;
import com.glory.spotflow_wallet.spotflow.dto.TransferDetailsResponse;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Deterministic fake implementation, active when spotflow.mock=true.
 * Lets the rest of the app (and a Postman demo) be exercised end-to-end with
 * zero network calls and no real Spotflow credentials.
 *
 * Behaviour: createTransfer immediately marks the transfer "successful" in memory,
 * so getTransferByReference will reflect that. Dynamic account creation always
 * succeeds with a fake account number. Nothing here is meant to mimic Spotflow's
 * actual test-mode timing/failure behaviour - it's purely for local development.
 */
public class MockSpotflowClient implements SpotflowClient {

    private final ConcurrentHashMap<String, TransferDetailsResponse> transfersByReference = new ConcurrentHashMap<>();

    @Override
    public CreateDynamicAccountResponse createDynamicAccount(CreateDynamicAccountRequest request) {
        String fakeAccountNumber = String.valueOf(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
        return new CreateDynamicAccountResponse(
                UUID.randomUUID().toString(),
                fakeAccountNumber,
                request.accountName(),
                "Mock Test Bank",
                "test",
                "temporary"
        );
    }

    @Override
    public TransferDetailsResponse createTransfer(CreateTransferRequest request) {
        TransferDetailsResponse response = new TransferDetailsResponse(
                request.reference(),
                "SPF-MOCK-" + UUID.randomUUID(),
                request.amount(),
                request.currency(),
                "bank_transfer",
                new TransferDetailsResponse.Destination(
                        request.destination().accountNumber(),
                        request.destination().accountName(),
                        request.destination().bankCode(),
                        request.destination().branchCode(),
                        "Mock Test Bank"
                ),
                request.narrations(),
                "successful"
        );
        transfersByReference.put(request.reference(), response);
        return response;
    }

    @Override
    public TransferDetailsResponse getTransferByReference(String reference) {
        TransferDetailsResponse response = transfersByReference.get(reference);
        if (response == null) {
            // Mimic Spotflow returning "not found" for a reference it never received.
            return new TransferDetailsResponse(reference, null, 0, null, null, null, null, "not_found");
        }
        return response;
    }
}
