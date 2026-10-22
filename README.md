
# Cinema Tickets (Java)


My implementation of the DWP cinema tickets exercise.


## What I changed

* `TicketServiceImpl`: the implementation. Payment and seat services are passed in through the constructor so they can be mocked in tests and swapped in production.
* `TicketTypeRequest`: made the class and its fields `final` so it is genuinely immutable, as the constraints require.
* `InvalidPurchaseException`: added a message constructor so each rejection explains why it was rejected.

Not changed, per the constraints: the `TicketService` interface and everything under `thirdparty.*`.

## How a purchase is processed

1. Validate the account id.
2. Validate each request and total the tickets per type (several requests for the same type are added together).
3. Validate the overall mix of tickets.
4. Work out the amount to pay and the number of seats.
5. Call `TicketPaymentService.makePayment`, then `SeatReservationService.reserveSeat`.

Every check runs before either external service is called, so a rejected request never causes a payment without seats, or seats without a payment.

## Prices and seats

| Type   | Price | Needs a seat |
| ------ | ----- | ------------ |
| ADULT  | £25   | yes          |
| CHILD  | £15   | yes          |
| INFANT | £0    | no (sits on an adult's lap) |

Prices and seat rules are exhaustive `switch` expressions over the ticket type enum, so adding a new type will not compile until it has a price and a seat rule.

## What counts as an invalid purchase

A request throws `InvalidPurchaseException` when:

* the account id is `null`, zero or negative
* no ticket requests are supplied, or the array is `null`
* any request is `null`, has a `null` type, or asks for zero or fewer tickets
* the total number of tickets (infants included) is more than 25
* child or infant tickets are requested without at least one adult ticket
* there are more infants than adults

## Assumptions and decisions

* Infant tickets count towards the 25 ticket limit. The rule says 25 tickets, and an infant ticket is still a ticket, even though it costs nothing and takes no seat.
* Each infant needs their own adult lap, so infants cannot outnumber adults. The brief says infants sit on an adult's lap; I read that as one infant per adult.
* A request for zero tickets is treated as malformed rather than ignored. Each request is a declaration of how many tickets of a type the customer wants, so zero (or a negative number) has no meaning.
* The running ticket total is checked as each request is added, using a `long`. This stops very large quantities from overflowing an `int` and slipping past the 25 ticket check.
* Payment is requested before seats are reserved. The brief says both always succeed, so the order only matters for readability, and taking payment first is the usual order in a real checkout.

## Tests

`TicketServiceImplTest` is grouped by business rule using JUnit 5 `@Nested` classes, with `@ParameterizedTest` for the valid price and seat combinations and for invalid input. Every rejection test also verifies that neither external service was called.
