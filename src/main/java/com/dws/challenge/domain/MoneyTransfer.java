package com.dws.challenge.domain;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MoneyTransfer {

    @NotNull
    @NotEmpty
    private final String accountFromId;

    @NotNull
    @NotEmpty
    private final String accountToId;

    @NotNull
    private final BigDecimal amount;

}
