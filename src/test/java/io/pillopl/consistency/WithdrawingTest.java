package io.pillopl.consistency;

import org.javamoney.moneta.Money;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static io.pillopl.consistency.Result.Failure;
import static io.pillopl.consistency.Result.Success;
import static org.javamoney.moneta.Money.of;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WithdrawingTest {

    VirtualCreditCardDatabase creditCardDatabase = new VirtualCreditCardDatabase();
    OwnershipDatabase ownershipDatabase = new OwnershipDatabase();

    WithdrawService withdrawService = new WithdrawService(creditCardDatabase, ownershipDatabase);
    AddLimitService addLimitService = new AddLimitService(creditCardDatabase);
    RepayService repayService = new RepayService(creditCardDatabase);
    CloseCycleService closeCycleService = new CloseCycleService(creditCardDatabase);
    OwnershipService ownershipService = new OwnershipService(ownershipDatabase);

    static OwnerId OSKAR = OwnerId.random();
    static OwnerId KUBA = OwnerId.random();

    @Test
    void canWithdraw() {
        //given
        CardId creditCard = newCreditCard();
        //and
        addLimitService.addLimit(creditCard, Money.of(100, "USD"));
        //and
        ownershipService.addAccess(creditCard, OSKAR);

        //when
        Result result = withdrawService.withdraw(creditCard, of(50, "USD"), OSKAR);

        //then
        assertEquals(Success, result);
        assertEquals(Money.of(50, "USD"), availableLimit(creditCard));
    }


    @Test
    void cantWithdrawMoreThanLimit() {
        //given
        CardId creditCard = newCreditCard();
        //and
        addLimitService.addLimit(creditCard, Money.of(100, "USD"));
        //and
        ownershipService.addAccess(creditCard, OSKAR);

        //when
        Result result = withdrawService.withdraw(creditCard, of(500, "USD"), OSKAR);

        //then
        assertEquals(Failure, result);
        assertEquals(Money.of(100, "USD"), availableLimit(creditCard));
    }

    @Test
    void cantWithdrawMoreThan45TimesInCycle() {
        //given
        CardId creditCard = newCreditCard();
        //and
        addLimitService.addLimit(creditCard, Money.of(100, "USD"));
        //and
        ownershipService.addAccess(creditCard, OSKAR);

        //and
        IntStream.range(1, 46).forEach(i -> withdrawService.withdraw(creditCard, of(1, "USD"), OSKAR));

        //when
        Result result = withdrawService.withdraw(creditCard, of(1, "USD"), OSKAR);

        //then
        assertEquals(Failure, result);
        assertEquals(Money.of(55, "USD"), availableLimit(creditCard));
    }

    @Test
    void canRepay() {
        //given
        CardId creditCard = newCreditCard();
        //and
        addLimitService.addLimit(creditCard, Money.of(100, "USD"));
        //and
        ownershipService.addAccess(creditCard, OSKAR);

        //and
        withdrawService.withdraw(creditCard, of(50, "USD"), OSKAR);

        //when
        Result result = repayService.repay(creditCard, of(40, "USD"));

        //then
        assertEquals(Success, result);
        assertEquals(Money.of(90, "USD"), availableLimit(creditCard));
    }

    @Test
    void canWithdrawInNextCycle() {
        //given
        CardId creditCard = newCreditCard();
        //and
        addLimitService.addLimit(creditCard, Money.of(100, "USD"));
        //and
        ownershipService.addAccess(creditCard, OSKAR);

        //and
        IntStream.range(1, 46).forEach(i -> withdrawService.withdraw(creditCard, of(1, "USD"), OSKAR));

        //and
        closeCycleService.close(creditCard);

        //when
        Result result = withdrawService.withdraw(creditCard, of(1, "USD"), OSKAR);

        //then
        assertEquals(Success, result);
        assertEquals(Money.of(54, "USD"), availableLimit(creditCard));
    }

    @Test
    void canWithdrawWhenNoAccess() {
        //given
        CardId creditCard = newCreditCard();
        //and
        addLimitService.addLimit(creditCard, Money.of(100, "USD"));

        //when
        Result result = withdrawService.withdraw(creditCard, of(50, "USD"), KUBA);

        //then
        assertEquals(Failure, result);
        assertEquals(Money.of(100, "USD"), availableLimit(creditCard));
    }

    @Test
    void canAddAccess() {
        //given
        CardId creditCard = newCreditCard();
        //and
        addLimitService.addLimit(creditCard, Money.of(100, "USD"));

        //when
        Result accessResult = ownershipService.addAccess(creditCard, KUBA);

        //then
        Result withdrawResult = withdrawService.withdraw(creditCard, of(50, "USD"), KUBA);
        assertEquals(Success, accessResult);
        assertEquals(Success, withdrawResult);
        assertEquals(Money.of(50, "USD"), availableLimit(creditCard));
    }

    @Test
    void cantAddMoreThan2Owners() {
        //given
        CardId creditCard = newCreditCard();
        //and
        addLimitService.addLimit(creditCard, Money.of(100, "USD"));

        //and
        Result firstAccess = ownershipService.addAccess(creditCard, KUBA);
        Result secondAccess = ownershipService.addAccess(creditCard, OSKAR);

        //when
        Result thirdAccess = ownershipService.addAccess(creditCard, OwnerId.random());

        //then
        assertEquals(Success, firstAccess);
        assertEquals(Success, secondAccess);
        assertEquals(Failure, thirdAccess);
    }

    @Test
    void canRevokeAccess() {
        //given
        CardId creditCard = newCreditCard();
        //and
        addLimitService.addLimit(creditCard, Money.of(100, "USD"));
        //and
        Result access = ownershipService.addAccess(creditCard, KUBA);
        //and
        Result withdrawResult = withdrawService.withdraw(creditCard, of(50, "USD"), KUBA);

        //when
        Result revoke = ownershipService.revokeAccess(creditCard, KUBA);

        //then
        Result secondWithdrawResult = withdrawService.withdraw(creditCard, of(50, "USD"), KUBA);

        //then
        assertEquals(Success, revoke);
        assertEquals(Success, withdrawResult);
        assertEquals(Failure, secondWithdrawResult);
    }

    CardId newCreditCard() {
        VirtualCreditCard virtualCreditCard = new VirtualCreditCard(CardId.random());
        creditCardDatabase.save(virtualCreditCard);
        return virtualCreditCard.id();
    }

    Money availableLimit(CardId creditCard) {
        return creditCardDatabase.find(creditCard).availableLimit();
    }


}