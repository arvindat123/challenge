package com.dws.challenge.repository;

import com.dws.challenge.domain.Account;
import com.dws.challenge.domain.MoneyTransfer;
import com.dws.challenge.exception.DuplicateAccountIdException;
import com.dws.challenge.exception.MoneyTransferException;

import java.util.List;

public interface AccountsRepository {

  void createAccount(Account account) throws DuplicateAccountIdException;

  Account getAccount(String accountId);

  void clearAccounts();

  void transferMoney(List<Account> transferFromToAccounts);
}
