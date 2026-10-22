package uk.gov.dwp.uc.pairtest;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

public class TicketServiceImpl implements TicketService {
    static final int MAX_TICKETS_PER_PURCHASE = 25;
    static final int ADULT_TICKET_PRICE = 25;
    static final int CHILD_TICKET_PRICE = 15;
    static final int INFANT_TICKET_PRICE = 0;

    private final TicketPaymentService ticketPaymentService;
    private final SeatReservationService seatReservationService;

    public TicketServiceImpl(TicketPaymentService ticketPaymentService,
                             SeatReservationService seatReservationService) {
        this.ticketPaymentService =
                Objects.requireNonNull(ticketPaymentService, "ticketPaymentService must not be null");
        this.seatReservationService =
                Objects.requireNonNull(seatReservationService, "seatReservationService must not be null");
    }
    /**
     * Should only have private methods other than the one below.
     */

    @Override
    public void purchaseTickets(Long accountId, TicketTypeRequest... ticketTypeRequests) throws InvalidPurchaseException {
        validateAccountId(accountId);
        Map<Type, Integer> ticketCounts = countTicketsByType(ticketTypeRequests);
        validateTicketMix(ticketCounts);

        int totalAmountToPay = calculateTotalAmount(ticketCounts);
        int totalSeatsToAllocate = calculateTotalSeats(ticketCounts);

        // Everything is validated before either external service is called,
        // so we can be sure that the payment and seat reservation will succeed.
        ticketPaymentService.makePayment(accountId, totalAmountToPay);
        seatReservationService.reserveSeat(accountId, totalSeatsToAllocate);
    }

        private void validateAccountId(Long accountId) {
        if (accountId == null || accountId <= 0) {
            throw new InvalidPurchaseException("Account id must be greater than zero");
        }
    }
    /**
     * Validates each request and aggregates quantities per ticket type, so
     * several requests for the same type (e.g. ADULT x2 and ADULT x3) are summed.
    */
    private Map<Type, Integer> countTicketsByType(TicketTypeRequest... ticketTypeRequests) {
        if (ticketTypeRequests == null || ticketTypeRequests.length == 0) {
            throw new InvalidPurchaseException("At least one ticket request is required");
        }

        Map<Type, Integer> ticketCounts = new EnumMap<>(Type.class);
        for (Type type : Type.values()) {
            ticketCounts.put(type, 0);
        }
        long totalTickets = 0;

        for (TicketTypeRequest request : ticketTypeRequests) {
            validateRequest(request);
            totalTickets += request.getNoOfTickets();
            if (totalTickets > MAX_TICKETS_PER_PURCHASE) {
                throw new InvalidPurchaseException("Cannot purchase more than "
                    + MAX_TICKETS_PER_PURCHASE + " tickets at once");
            }
            ticketCounts.merge(request.getTicketType(), request.getNoOfTickets(), Integer::sum);
        }
        return ticketCounts;
    }

    private void validateRequest(TicketTypeRequest request) {
        if (request == null) {
            throw new InvalidPurchaseException("Ticket request must not be null");
        }
        if (request.getTicketType() == null) {
            throw new InvalidPurchaseException("Ticket type must not be null");
        }
        if (request.getNoOfTickets() <= 0) {
            throw new InvalidPurchaseException("Number of tickets must be greater than zero for each request");
        }
    }

    private void validateTicketMix(Map<Type, Integer> ticketCounts) {
        int adults = ticketCounts.get(Type.ADULT);
        int children = ticketCounts.get(Type.CHILD);
        int infants = ticketCounts.get(Type.INFANT);

        if (adults == 0 && (children > 0 || infants > 0)) {
            throw new InvalidPurchaseException("At least one adult ticket is required when purchasing child or infant tickets");
        }
        if (infants > adults) {
            throw new InvalidPurchaseException("Each infant must be accompanied by an adult");
        }
    }


    private int calculateTotalAmount(Map<Type, Integer> ticketCounts) {
        int total = 0;
        for (Map.Entry<Type, Integer> entry : ticketCounts.entrySet()) {
            total += priceOf(entry.getKey()) * entry.getValue();
        }
        return total;
    }

    private int calculateTotalSeats(Map<Type, Integer> ticketCounts) {
        int seats = 0;
        for (Map.Entry<Type, Integer> entry : ticketCounts.entrySet()) {
            if (requiresSeat(entry.getKey())) {
                seats += entry.getValue();
            }
        }
        return seats;
    }

    // An exhaustive switch over the enum: adding a new ticket type will not
    // compile until it has a price, so a type can never be silently free.
    private static int priceOf(Type type) {
        return switch (type) {
            case ADULT -> ADULT_TICKET_PRICE;
            case CHILD -> CHILD_TICKET_PRICE;
            case INFANT -> INFANT_TICKET_PRICE;
        };
    }

    private static boolean requiresSeat(Type type) {
        return switch (type) {
            case ADULT, CHILD -> true;
            case INFANT -> false;
        };
    }
}
