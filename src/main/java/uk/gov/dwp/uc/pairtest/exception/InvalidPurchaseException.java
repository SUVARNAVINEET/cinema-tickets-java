package uk.gov.dwp.uc.pairtest.exception;

public class InvalidPurchaseException extends RuntimeException {
    /**It’s not in thirdparty, so adding a message constructor.*/

    public InvalidPurchaseException() {
        super();
    }
    public InvalidPurchaseException(String message) {
        super(message);
    }

}
