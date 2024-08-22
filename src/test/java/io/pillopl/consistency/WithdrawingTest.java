package io.pillopl.consistency;

import org.javamoney.moneta.Money;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.money.Monetary;
import java.util.stream.IntStream;

import static io.pillopl.consistency.Result.Failure;
import static io.pillopl.consistency.Result.Success;
import static org.javamoney.moneta.Money.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class WithdrawingTest {
    EventStore eventStore = new EventStore();
    BillingCycleDatabase billingCycleDatabase = new BillingCycleDatabase(eventStore);
    VirtualCreditCardDatabase creditCardDatabase = new VirtualCreditCardDatabase(eventStore);
    OwnershipDatabase ownershipDatabase = new OwnershipDatabase();

    BillingCycleEventHandler eventHandler = new BillingCycleEventHandler(creditCardDatabase, billingCycleDatabase);
    WithdrawService withdrawService = new WithdrawService(billingCycleDatabase, ownershipDatabase);
    AddLimitService addLimitService = new AddLimitService(creditCardDatabase);
    RepayService repayService = new RepayService(billingCycleDatabase);
    BillingCycleService billingCycleService = new BillingCycleService(creditCardDatabase, billingCycleDatabase);
    OwnershipService ownershipService = new OwnershipService(ownershipDatabase);

    static OwnerId OSKAR = OwnerId.random();
    static OwnerId KUBA = OwnerId.random();

    @BeforeEach
    void beforeEach(){
        eventStore.subscribe(eventHandler::handle);
    }

    @Test
    void canWithdraw() {
        //given
        CardId creditCard = newCreditCard();
        //and
        addLimitService.addLimit(creditCard, Money.of(100, "USD"));
        //and
        ownershipService.addAccess(creditCard, OSKAR);
        //and
        var cycleId = openBillingCycle(creditCard);

        //when
        Result result = withdrawService.withdraw(cycleId, of(50, "USD"), OSKAR);

        //then
        assertEquals(Success, result);
        assertEquals(Money.of(50, "USD"), availableLimit(cycleId));
    }


    @Test
    void cantWithdrawMoreThanLimit() {
        //given
        CardId creditCard = newCreditCard();
        //and
        addLimitService.addLimit(creditCard, Money.of(100, "USD"));
        //and
        ownershipService.addAccess(creditCard, OSKAR);
        //and
        var cycleId = openBillingCycle(creditCard);

        //when
        Result result = withdrawService.withdraw(cycleId, of(500, "USD"), OSKAR);

        //then
        assertEquals(Failure, result);
        assertEquals(Money.of(100, "USD"), availableLimit(cycleId));
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
        var cycleId = openBillingCycle(creditCard);

        //and
        IntStream.range(1, 46).forEach(i -> withdrawService.withdraw(cycleId, of(1, "USD"), OSKAR));

        //when
        Result result = withdrawService.withdraw(cycleId, of(1, "USD"), OSKAR);

        //then
        assertEquals(Failure, result);
        assertEquals(Money.of(55, "USD"), availableLimit(cycleId));
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
        var cycleId = openBillingCycle(creditCard);

        //and
        withdrawService.withdraw(cycleId, of(50, "USD"), OSKAR);

        //when
        Result result = repayService.repay(cycleId, of(40, "USD"));

        //then
        assertEquals(Success, result);
        assertEquals(Money.of(90, "USD"), availableLimit(cycleId));
    }

    @Test
    void canWithdrawInNextCycleIfWholeDebtWasPaid() {
        //given
        CardId creditCard = newCreditCard();
        //and
        addLimitService.addLimit(creditCard, Money.of(100, "USD"));
        //and
        ownershipService.addAccess(creditCard, OSKAR);
        //and
        var initialCycleId = openBillingCycle(creditCard);

        //and

        withdrawService.withdraw(initialCycleId, of(100, "USD"), OSKAR);
        repayService.repay(initialCycleId, of(100, "USD"));

        //and
        billingCycleService.close(initialCycleId);
        //and
        var cycleId = openBillingCycle(creditCard);

        //when
        Result result = withdrawService.withdraw(cycleId, of(1, "USD"), OSKAR);

        //then
        assertEquals(Success, result);
        assertEquals(Money.of(99, "USD"), availableLimit(cycleId));
    }

    @Test
    void canWithdrawWhenNoAccess() {
        //given
        CardId creditCard = newCreditCard();
        //and
        addLimitService.addLimit(creditCard, Money.of(100, "USD"));
        //and
        var cycleId = openBillingCycle(creditCard);

        //when
        Result result = withdrawService.withdraw(cycleId, of(50, "USD"), KUBA);

        //then
        assertEquals(Failure, result);
        assertEquals(Money.of(100, "USD"), availableLimit(cycleId));
    }

    @Test
    void canAddAccess() {
        //given
        CardId creditCard = newCreditCard();
        //and
        addLimitService.addLimit(creditCard, Money.of(100, "USD"));
        //and
        var cycleId = openBillingCycle(creditCard);

        //when
        Result accessResult = ownershipService.addAccess(creditCard, KUBA);

        //then
        Result withdrawResult = withdrawService.withdraw(cycleId, of(50, "USD"), KUBA);
        assertEquals(Success, accessResult);
        assertEquals(Success, withdrawResult);
        assertEquals(Money.of(50, "USD"), availableLimit(cycleId));
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
        var cycleId = openBillingCycle(creditCard);
        //and
        Result withdrawResult = withdrawService.withdraw(cycleId, of(50, "USD"), KUBA);

        //when
        Result revoke = ownershipService.revokeAccess(creditCard, KUBA);

        //then
        Result secondWithdrawResult = withdrawService.withdraw(cycleId, of(50, "USD"), KUBA);

        //then
        assertEquals(Success, revoke);
        assertEquals(Success, withdrawResult);
        assertEquals(Failure, secondWithdrawResult);
    }

    CardId newCreditCard() {
        VirtualCreditCard virtualCreditCard = VirtualCreditCard.create(CardId.random(), Monetary.getCurrency("USD"));
        creditCardDatabase.save(virtualCreditCard, 0);
        return virtualCreditCard.id();
    }

    Money availableLimit(BillingCycleId cycleId) {
        return billingCycleDatabase.find(cycleId).availableLimit();
    }

    BillingCycleId openBillingCycle(CardId creditCard) {
        var result = billingCycleService.openNextCycle(creditCard);
        assertEquals(Success, result, "Cannot open next billing cycle!");

        var cycleId = billingCycleService.getCurrentlyOpenedBillingCycleId(creditCard);
        if (cycleId.isEmpty()) fail("No active cycle");

        return cycleId.get();
    }

    EventStore eventStoreWithSubscribers(BillingCycleEventHandler eventHandler){
        var eventStore = new EventStore();

        eventStore.subscribe(eventHandler::handle);


        return eventStore;
    }
}
