package io.pillopl.consistency;

import org.javamoney.moneta.Money;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static io.pillopl.consistency.Result.Failure;
import static io.pillopl.consistency.Result.Success;
import static org.javamoney.moneta.Money.of;
import static org.junit.jupiter.api.Assertions.assertEquals;

class VirtualCreditCardTest {

    @Test
    void canWithdraw() {
        //given
        VirtualCreditCard creditCard = VirtualCreditCard.withLimit(of(100, "USD"));

        //when
        Result result = creditCard.withdraw(of(50, "USD"));

        //then
        assertEquals(Success, result);
        assertEquals(Money.of(50, "USD"), creditCard.availableLimit());
    }

    @Test
    void cantWithdrawMoreThanLimit() {
        //given
        VirtualCreditCard creditCard = VirtualCreditCard.withLimit(of(100, "USD"));

        //when
        Result result = creditCard.withdraw(of(500, "USD"));

        //then
        assertEquals(Failure, result);
        assertEquals(Money.of(100, "USD"), creditCard.availableLimit());
    }

    @Test
    void cantWithdrawMoreThan45TimesInCycle() {
        //given
        VirtualCreditCard creditCard = VirtualCreditCard.withLimit(of(100, "USD"));

        //and
        IntStream.range(1, 46).forEach(i -> creditCard.withdraw(of(1, "USD")));

        //when
        Result result = creditCard.withdraw(of(1, "USD"));

        //then
        assertEquals(Failure, result);
        assertEquals(Money.of(55, "USD"), creditCard.availableLimit());
    }

    @Test
    void canRepay() {
        //given
        VirtualCreditCard creditCard = VirtualCreditCard.withLimit(of(100, "USD"));
        //and
        creditCard.withdraw(of(50, "USD"));

        //when
        Result result = creditCard.repay(of(40, "USD"));

        //then
        assertEquals(Success, result);
        assertEquals(Money.of(90, "USD"), creditCard.availableLimit());
    }

    @Test
    void canWithdrawInNextCycle() {
        //given
        VirtualCreditCard creditCard = VirtualCreditCard.withLimit(of(100, "USD"));

        //and
        IntStream.range(1, 46).forEach(i -> creditCard.withdraw(of(1, "USD")));

        //and
        creditCard.closeCycle();

        //when
        Result result = creditCard.withdraw(of(1, "USD"));

        //then
        assertEquals(Success, result);
        assertEquals(Money.of(54, "USD"), creditCard.availableLimit());
    }

}