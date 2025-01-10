package com.dws.challenge.service;

import com.dws.challenge.domain.Account;
import com.dws.challenge.domain.MoneyTransfer;
import com.dws.challenge.exception.MoneyTransferException;
import com.dws.challenge.repository.AccountsRepository;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
public class AccountsService {

    @Getter
    private final AccountsRepository accountsRepository;

    @Autowired
    private EmailNotificationService emailNotificationService;

    @Autowired
    public AccountsService(AccountsRepository accountsRepository) {
        this.accountsRepository = accountsRepository;
    }

    public void createAccount(Account account) {
        this.accountsRepository.createAccount(account);
    }

    public Account getAccount(String accountId) {
        return this.accountsRepository.getAccount(accountId);
    }

    public void transferAmount(MoneyTransfer transferMoney) throws MoneyTransferException {
        log.info("Money transfer started.");
        Lock lockFrom = new ReentrantLock();
        Lock lockTo = new ReentrantLock();

        List<Account> transferFromToAccounts = new ArrayList<>();
        BigDecimal transferAmount = transferMoney.getAmount();
        if (transferAmount == null || transferAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new MoneyTransferException("Transfer amount must be positive.");
        }
        Account accountFromDetails = this.accountsRepository.getAccount(transferMoney.getAccountFromId());
        Account accountToDetails = this.accountsRepository.getAccount(transferMoney.getAccountToId());
        if (accountFromDetails == null || accountToDetails == null) {
            throw new MoneyTransferException("Either of accounts does not exist. Please provide valid account ID.");
        } else if (accountFromDetails == accountToDetails) {
            throw new MoneyTransferException("From and To accounts should not be same.");
        }

        if (accountFromDetails.getAccountId().compareTo(accountToDetails.getAccountId()) < 0) {
            lockFrom.lock();
            lockTo.lock();
        } else {
            lockTo.lock();
            lockFrom.lock();
        }
        try {
            BigDecimal balanceInFromAccount = accountFromDetails.getBalance();
            if (balanceInFromAccount.compareTo(transferAmount) < 0) {
                throw new MoneyTransferException("Overdrafts facility is not available. Please try to transfer with smaller amount.");
            } else {
                accountFromDetails.setBalance(balanceInFromAccount.subtract(transferAmount));
                accountToDetails.setBalance(accountToDetails.getBalance().add(transferAmount));
                transferFromToAccounts.add(accountFromDetails);
                transferFromToAccounts.add(accountToDetails);
                this.accountsRepository.transferMoney(transferFromToAccounts);
            }
            log.info("Sending notification to Account owner started.");
            String MoneyFromAccountNotification = String.format("Amount %s has been transferred from your account to Account ID: %s.", transferAmount, accountToDetails.getAccountId());
            String MoneyToAccountNotification = String.format("Amount %s has been received in your account from Account ID: %s.", transferAmount, accountFromDetails.getAccountId());

            emailNotificationService.notifyAboutTransfer(accountFromDetails, MoneyFromAccountNotification);
            emailNotificationService.notifyAboutTransfer(accountToDetails, MoneyToAccountNotification);
        } finally {
            lockFrom.unlock();
            lockTo.unlock();
        }
        log.info("Money transferred completed successfully.");
    }
}
