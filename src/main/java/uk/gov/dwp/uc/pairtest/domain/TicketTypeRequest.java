package uk.gov.dwp.uc.pairtest.domain;

/**
 * Immutable Object
 * The class is final and both fields are final
 * Business validation -(positive
 * quantities, non null type)
 * is done by the TicketService so that every
 * rejection is reported in one consistent way,
 * as an InvalidPurchaseException
 */

public final class TicketTypeRequest {

    private final int noOfTickets;
    private final Type type;

    public TicketTypeRequest(Type type, int noOfTickets) {
        this.type = type;
        this.noOfTickets = noOfTickets;
    }

    public int getNoOfTickets() {
        return noOfTickets;
    }

    public Type getTicketType() {
        return type;
    }

    @Override
    public String toString() {
        return "TicketTypeRequest{type=" + type + ", noOfTickets=" + noOfTickets + "}";
    }

    public enum Type {
        ADULT, CHILD , INFANT
    }

}
