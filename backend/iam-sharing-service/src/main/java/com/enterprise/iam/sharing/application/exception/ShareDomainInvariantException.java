package com.enterprise.iam.sharing.application.exception;

/**
 * A security or domain invariant was violated by the request. Maps to the
 * contract's 422 Unprocessable response ({@code IAM_SHARE_INVARIANT_VIOLATION}).
 */
public class ShareDomainInvariantException extends RuntimeException {

    public static final String CODE = "IAM_SHARE_INVARIANT_VIOLATION";

    public ShareDomainInvariantException(String message) {
        super(message);
    }
}
