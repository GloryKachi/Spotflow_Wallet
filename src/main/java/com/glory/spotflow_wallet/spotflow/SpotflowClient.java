package com.glory.spotflow_wallet.spotflow;

import com.glory.spotflow_wallet.spotflow.dto.CreateDynamicAccountRequest;
import com.glory.spotflow_wallet.spotflow.dto.CreateDynamicAccountResponse;
import com.glory.spotflow_wallet.spotflow.dto.CreateTransferRequest;
import com.glory.spotflow_wallet.spotflow.dto.TransferDetailsResponse;

/**
 * The only contract core business logic (wallet/transaction services) is allowed to depend on.
 * Two implementations exist:
 *  - {@link MockSpotflowClient}: used when spotflow.mock=true, no network calls, deterministic fake data.
 *  - {@link HttpSpotflowClient}: real calls to the Spotflow API using the configured secret key.
 *
 * Swapping between them is purely a config flag (spotflow.mock) - nothing outside this package
 * needs to know or care which one is active.
 */
public interface SpotflowClient {

    /**
     * Creates a temporary dynamic (virtual) account for the user to pay into.
     */
    CreateDynamicAccountResponse createDynamicAccount(CreateDynamicAccountRequest request);

    /**
     * Initiates a single bank disbursement (payout).
     */
    TransferDetailsResponse createTransfer(CreateTransferRequest request);

    /**
     * Looks up the true status of a transfer by the reference WE generated
     * (not the spotflowReference). Used by the fallback reconciliation worker.
     */
    TransferDetailsResponse getTransferByReference(String reference);
}
