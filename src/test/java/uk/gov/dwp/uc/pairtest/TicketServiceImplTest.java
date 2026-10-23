package uk.gov.dwp.uc.pairtest;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type.ADULT;
import static uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type.CHILD;
import static uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type.INFANT;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

class TicketServiceImplTest {

    private static final long VALID_ACCOUNT_ID = 1L;

    private TicketPaymentService paymentService;
    private SeatReservationService seatReservationService;
    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        paymentService = mock(TicketPaymentService.class);
        seatReservationService = mock(SeatReservationService.class);
        ticketService = new TicketServiceImpl(paymentService, seatReservationService);
    }

    private static TicketTypeRequest adults(int n) {
        return new TicketTypeRequest(ADULT, n);
    }

    private static TicketTypeRequest children(int n) {
        return new TicketTypeRequest(CHILD, n);
    }

    private static TicketTypeRequest infants(int n) {
        return new TicketTypeRequest(INFANT, n);
    }

    private void assertRejected(Long accountId, TicketTypeRequest... requests) {
        assertThrows(InvalidPurchaseException.class, () -> ticketService.purchaseTickets(accountId, requests));
        verifyNoInteractions(paymentService, seatReservationService);
    }

    @Nested
    @DisplayName("Account id validation")
    class AccountValidation {

        @ParameterizedTest
        @NullSource
        @ValueSource(longs = { 0L, -1L, Long.MIN_VALUE })
        void rejectsInvalidAccountIds(Long accountId) {
            assertRejected(accountId, adults(1));
        }

        @Test
        void acceptsLargeAccountId() {
            ticketService.purchaseTickets(Long.MAX_VALUE, adults(1));
            verify(paymentService).makePayment(Long.MAX_VALUE, 25);
            verify(seatReservationService).reserveSeat(Long.MAX_VALUE, 1);
        }
    }

    @Nested
    @DisplayName("Malformed ticket requests")
    class MalformedRequests {

        @Test
        void rejectsNullRequestArray() {
            assertRejected(VALID_ACCOUNT_ID, (TicketTypeRequest[]) null);
        }

        @Test
        void rejectsNoRequests() {
            assertRejected(VALID_ACCOUNT_ID);
        }

        @Test
        void rejectsNullRequestElement() {
            assertRejected(VALID_ACCOUNT_ID, adults(1), null);
        }

        @Test
        void rejectsNullTicketType() {
            assertRejected(VALID_ACCOUNT_ID, adults(1), new TicketTypeRequest(null, 1));
        }

        @ParameterizedTest
        @ValueSource(ints = { 0, -1, Integer.MIN_VALUE })
        void rejectsNonPositiveQuantity(int quantity) {
            assertRejected(VALID_ACCOUNT_ID, adults(1), children(quantity));
        }

        @Test
        void negativeQuantityCannotBeUsedToSneakPastTheLimit() {
            assertRejected(VALID_ACCOUNT_ID, adults(30), adults(-10));
        }

    }

    @Nested
    @DisplayName("Maximum of 25 tickets per purchase")
    class MaximumTickets {

        @Test
        void rejects26Tickets() {
            assertRejected(VALID_ACCOUNT_ID, adults(26));
        }

        @Test
        void rejectsMoreThan25AcrossSeveralRequests() {
            assertRejected(VALID_ACCOUNT_ID, adults(10), children(10), infants(6));
        }

        @Test
        void rejectsQuantitiesThatWouldOverflowAnInt() {
            assertRejected(VALID_ACCOUNT_ID, adults(Integer.MAX_VALUE), adults(Integer.MAX_VALUE));
        }
    }

    @Nested
    @DisplayName("Valid purchases: correct amount paid and seats reserved")
    class ValidPurchases {

        @ParameterizedTest(name = "{0}")
        @MethodSource("validPurchases")
        void paysCorrectAmountAndReservesCorrectSeats(String description,
                TicketTypeRequest[] requests,
                int expectedAmount,
                int expectedSeats) {
            ticketService.purchaseTickets(VALID_ACCOUNT_ID, requests);

            verify(paymentService).makePayment(VALID_ACCOUNT_ID, expectedAmount);
            verify(seatReservationService).reserveSeat(VALID_ACCOUNT_ID, expectedSeats);
            verifyNoMoreInteractions(paymentService, seatReservationService);
        }

        static Stream<Arguments> validPurchases() {
            return Stream.of(
                    arguments("1 adult", new TicketTypeRequest[] { adults(1) }, 25, 1),
                    arguments("1 adult, 1 child", new TicketTypeRequest[] { adults(1), children(1) }, 40, 2),
                    arguments("1 adult, 1 infant (infant pays nothing, no seat)",
                            new TicketTypeRequest[] { adults(1), infants(1) }, 25, 1),
                    arguments("2 adults, 3 children, 2 infants",
                            new TicketTypeRequest[] { adults(2), children(3), infants(2) }, 95, 5),
                    arguments("requests for the same type are aggregated",
                            new TicketTypeRequest[] { adults(2), children(1), adults(3) }, 140, 6),
                    arguments("order of requests does not matter",
                            new TicketTypeRequest[] { infants(1), children(2), adults(1) }, 55, 3),
                    arguments("exactly 25 tickets is allowed",
                            new TicketTypeRequest[] { adults(25) }, 625, 25),
                    arguments("infants count towards the 25 ticket limit but not seats",
                            new TicketTypeRequest[] { adults(13), infants(12) }, 325, 13));
        }

        @Test
        void paymentIsRequestedBeforeSeatReservation() {
            ticketService.purchaseTickets(VALID_ACCOUNT_ID, adults(1));

            var order = inOrder(paymentService, seatReservationService);
            order.verify(paymentService).makePayment(VALID_ACCOUNT_ID, 25);
            order.verify(seatReservationService).reserveSeat(VALID_ACCOUNT_ID, 1);
        }
    }

    @Nested
    @DisplayName("Child and Infant tickets need an Adult")
    class AdultRequired {

        @Test
        void rejectsChildWithoutAdult() {
            assertRejected(VALID_ACCOUNT_ID, children(1));
        }

        @Test
        void rejectsInfantWithoutAdult() {
            assertRejected(VALID_ACCOUNT_ID, infants(1));
        }

        @Test
        void rejectsChildAndInfantWithoutAdult() {
            assertRejected(VALID_ACCOUNT_ID, children(2), infants(1));
        }

        @Test
        void rejectsMoreInfantsThanAdults() {
            assertRejected(VALID_ACCOUNT_ID, adults(1), infants(2));
        }
    }

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        void requiresPaymentService() {
            assertThrows(NullPointerException.class, () -> new TicketServiceImpl(null, seatReservationService));
        }

        @Test
        void requiresSeatReservationService() {
            assertThrows(NullPointerException.class, () -> new TicketServiceImpl(paymentService, null));
        }
    }
}
