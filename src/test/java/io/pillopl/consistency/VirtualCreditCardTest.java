package io.pillopl.consistency;

import org.javamoney.moneta.Money;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static io.pillopl.consistency.Result.Failure;
import static io.pillopl.consistency.Result.Success;
import static org.javamoney.moneta.Money.of;
import static org.junit.jupiter.api.Assertions.assertEquals;

class VirtualCreditCardTest {

    static OwnerId OSKAR = OwnerId.random();
    static OwnerId KUBA = OwnerId.random();

    @Test
    void canWithdraw() {
        //given
        VirtualCreditCard creditCard = VirtualCreditCard.withLimitAndOwner(of(100, "USD"), OSKAR);

        //when
        Result result = creditCard.withdraw(of(50, "USD"), OSKAR);

        //then
        assertEquals(Success, result);
        assertEquals(Money.of(50, "USD"), creditCard.availableLimit());
    }

    @Test
    void cantWithdrawMoreThanLimit() {
        //given
        VirtualCreditCard creditCard = VirtualCreditCard.withLimitAndOwner(of(100, "USD"), OSKAR);

        //when
        Result result = creditCard.withdraw(of(500, "USD"), OSKAR);

        //then
        assertEquals(Failure, result);
        assertEquals(Money.of(100, "USD"), creditCard.availableLimit());
    }

    @Test
    void cantWithdrawMoreThan45TimesInCycle() {
        //given
        VirtualCreditCard creditCard = VirtualCreditCard.withLimitAndOwner(of(100, "USD"), OSKAR);

        //and
        IntStream.range(1, 46).forEach(i -> creditCard.withdraw(of(1, "USD"), OSKAR));

        //when
        Result result = creditCard.withdraw(of(1, "USD"), OSKAR);

        //then
        assertEquals(Failure, result);
        assertEquals(Money.of(55, "USD"), creditCard.availableLimit());
    }

    @Test
    void canRepay() {
        //given
        VirtualCreditCard creditCard = VirtualCreditCard.withLimitAndOwner(of(100, "USD"), OSKAR);
        //and
        creditCard.withdraw(of(50, "USD"), OSKAR);

        //when
        Result result = creditCard.repay(of(40, "USD"));

        //then
        assertEquals(Success, result);
        assertEquals(Money.of(90, "USD"), creditCard.availableLimit());
    }

    @Test
    void canWithdrawInNextCycle() {
        //given
        VirtualCreditCard creditCard = VirtualCreditCard.withLimitAndOwner(of(100, "USD"), OSKAR);

        //and
        IntStream.range(1, 46).forEach(i -> creditCard.withdraw(of(1, "USD"), OSKAR));

        //and
        creditCard.closeCycle();

        //when
        Result result = creditCard.withdraw(of(1, "USD"), OSKAR);

        //then
        assertEquals(Success, result);
        assertEquals(Money.of(54, "USD"), creditCard.availableLimit());
    }

    @Test
    void canWithdrawWhenNoAccess() {
        //given
        VirtualCreditCard creditCard = VirtualCreditCard.withLimitAndOwner(of(100, "USD"), OSKAR);

        //when
        Result result = creditCard.withdraw(of(50, "USD"), KUBA);

        //then
        assertEquals(Failure, result);
        assertEquals(Money.of(100, "USD"), creditCard.availableLimit());
    }

    @Test
    void canAddAccess() {
        //given
        VirtualCreditCard creditCard = VirtualCreditCard.withLimitAndOwner(of(100, "USD"), OSKAR);

        //when
        Result accessResult = creditCard.addAccess(KUBA);

        //then
        Result withdrawResult = creditCard.withdraw(of(50, "USD"), KUBA);
        assertEquals(Success, accessResult);
        assertEquals(Success, withdrawResult);
        assertEquals(Money.of(50, "USD"), creditCard.availableLimit());
    }

    @Test
    void cantAddMoreThan2Owners() {
        //given
        VirtualCreditCard creditCard = VirtualCreditCard.withLimitAndOwner(of(100, "USD"), OSKAR);
        //and
        Result secondAccess = creditCard.addAccess(KUBA);

        //when
        Result thirdAccess = creditCard.addAccess(OwnerId.random());

        //then
        assertEquals(Success, secondAccess);
        assertEquals(Failure, thirdAccess);
    }

    @Test
    void cantRevokeAccess() {
        //given
        VirtualCreditCard creditCard = VirtualCreditCard.withLimitAndOwner(of(100, "USD"), OSKAR);
        //and
        Result access = creditCard.addAccess(KUBA);
        //and
        Result withdrawResult = creditCard.withdraw(of(50, "USD"), KUBA);

        //when
        Result revoke = creditCard.revokeAccess(KUBA);

        //then
        Result secondWithdrawResult = creditCard.withdraw(of(50, "USD"), KUBA);

        //then
        assertEquals(Success, revoke);
        assertEquals(Success, withdrawResult);
        assertEquals(Failure, secondWithdrawResult);
    }

}