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
        Map<Type, Integer> ticketCounts = countTicketsByType(ticketTypeRequests);

        int totalAmountToPay = calculateTotalAmount(ticketCounts);
        int totalSeatsToAllocate = calculateTotalSeats(ticketCounts);

        ticketPaymentService.makePayment(accountId, totalAmountToPay);
        seatReservationService.reserveSeat(accountId, totalSeatsToAllocate);
    }

    private Map<Type, Integer> countTicketsByType(TicketTypeRequest... ticketTypeRequests) {
        Map<Type, Integer> ticketCounts = new EnumMap<>(Type.class);
        for (Type type : Type.values()) {
            ticketCounts.put(type, 0);
        }

        for (TicketTypeRequest request : ticketTypeRequests) {
            ticketCounts.merge(request.getTicketType(), request.getNoOfTickets(), Integer::sum);
        }
        return ticketCounts;
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
