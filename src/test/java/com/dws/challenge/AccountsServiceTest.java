package com.dws.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import java.math.BigDecimal;

import com.dws.challenge.domain.Account;
import com.dws.challenge.domain.MoneyTransfer;
import com.dws.challenge.exception.DuplicateAccountIdException;
import com.dws.challenge.exception.MoneyTransferException;
import com.dws.challenge.service.AccountsService;
import com.dws.challenge.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

@ExtendWith(SpringExtension.class)
@SpringBootTest
class AccountsServiceTest {

    @Autowired
    private AccountsService accountsService;

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private NotificationService notificationService;

    private Account accountFrom;
    private Account accountTo;

    @BeforeEach
    void prepareMockMvc() {
        this.mockMvc = webAppContextSetup(this.webApplicationContext).build();

        // Reset the existing accounts before each test.
        accountsService.getAccountsRepository().clearAccounts();
    }

    @Test
    void addAccount() {
        Account account = new Account("Id-123");
        account.setBalance(new BigDecimal(1000));
        this.accountsService.createAccount(account);

        assertThat(this.accountsService.getAccount("Id-123")).isEqualTo(account);
    }

    @Test
    void addAccount_failsOnDuplicateId() {
        String uniqueId = "Id-" + System.currentTimeMillis();
        Account account = new Account(uniqueId);
        this.accountsService.createAccount(account);

        try {
            this.accountsService.createAccount(account);
            fail("Should have failed when adding duplicate account");
        } catch (DuplicateAccountIdException ex) {
            assertThat(ex.getMessage()).isEqualTo("Account id " + uniqueId + " already exists!");
        }
    }

    @Test
    void transferAmountSuccess() {
        setUpAccount();
        MoneyTransfer transferRequest = new MoneyTransfer(accountFrom.getAccountId(), accountTo.getAccountId(), new BigDecimal("2500"));
        this.accountsService.transferAmount(transferRequest);
        notificationService.notifyAboutTransfer(accountFrom, "Money has been transferred from.");
        notificationService.notifyAboutTransfer(accountTo, "Money has been transferred To.");
        assertThat(accountFrom.getBalance()).isEqualTo("2500");
        assertThat(accountTo.getBalance()).isEqualTo("3500");
    }

    @Test
    void transferAmountWithConcurrentTransfer() throws InterruptedException {
        setUpAccount();
        MoneyTransfer transferRequestFirst = new MoneyTransfer(accountFrom.getAccountId(), accountTo.getAccountId(), new BigDecimal("2500"));
        MoneyTransfer transferRequestSecond = new MoneyTransfer(accountFrom.getAccountId(), accountTo.getAccountId(), new BigDecimal("1500"));
        Thread thread1 = new Thread(() -> {
            this.accountsService.transferAmount(transferRequestFirst);
        });
        Thread thread2 = new Thread(() -> {
            this.accountsService.transferAmount(transferRequestSecond);
        });
        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();
        assertTrue(accountFrom.getBalance().compareTo(BigDecimal.ZERO) >= 0);
    }

    @Test
    void transferAmountWithInsufficientBalance() {
        setUpAccount();
        MoneyTransfer transferRequest = new MoneyTransfer(accountFrom.getAccountId(), accountTo.getAccountId(), new BigDecimal("7500"));
        MoneyTransferException exception = assertThrows(MoneyTransferException.class, () -> {
            this.accountsService.transferAmount(transferRequest);
        });
        assertThat("Overdrafts facility is not available. Please try to transfer with smaller amount.").isEqualTo(exception.getMessage());
    }

    @Test
    void transferAmountWithNullAmount() {
        setUpAccount();
        MoneyTransfer transferRequest = new MoneyTransfer(accountFrom.getAccountId(), accountTo.getAccountId(), null);
        MoneyTransferException exception = assertThrows(MoneyTransferException.class, () -> {
            this.accountsService.transferAmount(transferRequest);
        });
        assertThat("Transfer amount must be positive.").isEqualTo(exception.getMessage());
    }

    @Test
    void transferAmountWithNegativeAmount() {
        setUpAccount();
        MoneyTransfer transferRequest = new MoneyTransfer(accountFrom.getAccountId(), accountTo.getAccountId(), new BigDecimal("-5000"));
        MoneyTransferException exception = assertThrows(MoneyTransferException.class, () -> {
            this.accountsService.transferAmount(transferRequest);
        });
        assertThat("Transfer amount must be positive.").isEqualTo(exception.getMessage());
    }

    @Test
    void transferAmountWithNonExistingAccounts() {
        setUpAccount();
        MoneyTransfer transferRequest = new MoneyTransfer("ID-00000", "ID-11111", new BigDecimal("5000"));
        MoneyTransferException exception = assertThrows(MoneyTransferException.class, () -> {
            this.accountsService.transferAmount(transferRequest);
        });
        assertThat("Either of accounts does not exist. Please provide valid account ID.").isEqualTo(exception.getMessage());
    }

    @Test
    void transferAmountBetweenSameAccounts() {
        setUpAccount();
        MoneyTransfer transferRequest = new MoneyTransfer("ID-12345", "ID-12345", new BigDecimal("5000"));
        MoneyTransferException exception = assertThrows(MoneyTransferException.class, () -> {
            this.accountsService.transferAmount(transferRequest);
        });
        assertThat("From and To accounts should not be same.").isEqualTo(exception.getMessage());
    }

    private void setUpAccount(){
        accountFrom = new Account("ID-12345", new BigDecimal("5000"));
        accountTo = new Account("ID-12346", new BigDecimal("1000"));
        this.accountsService.createAccount(accountFrom);
        this.accountsService.createAccount(accountTo);
    }
}
