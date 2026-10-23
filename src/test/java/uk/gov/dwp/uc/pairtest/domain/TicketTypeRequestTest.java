package uk.gov.dwp.uc.pairtest.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

public class TicketTypeRequestTest {

    @Test
    void shouldReturnTicketTypeAndQuantity() {
        TicketTypeRequest request = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 2);

        assertEquals(TicketTypeRequest.Type.ADULT, request.getTicketType());
        assertEquals(2, request.getNoOfTickets());
    }

    @Test
    void isImmutable() {
        assertTrue(Modifier.isFinal(TicketTypeRequest.class.getModifiers()), "class should be final");
        for (Field field : TicketTypeRequest.class.getDeclaredFields()) {
            if (!field.isSynthetic()) {
                assertTrue(Modifier.isFinal(field.getModifiers()), field.getName() + " should be final");
            }
        }
    }

    @Test
    void shouldPreserveEachTicketType() {
        for (TicketTypeRequest.Type type : TicketTypeRequest.Type.values()) {
            TicketTypeRequest request = new TicketTypeRequest(type, 1);

            assertEquals(type, request.getTicketType());
        }
    }

    @Test
    void shouldFormatRequestAsString() {
        TicketTypeRequest request = new TicketTypeRequest(TicketTypeRequest.Type.CHILD, 3);

        assertEquals("TicketTypeRequest{type=CHILD, noOfTickets=3}", request.toString());
    }
}
