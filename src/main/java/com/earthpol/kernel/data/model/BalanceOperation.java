package com.earthpol.kernel.data.model;

import java.math.BigDecimal;

public record BalanceOperation(boolean success, BigDecimal amount, BigDecimal balance, String message) {

    public static BalanceOperation success(BigDecimal amount, BigDecimal balance) {
        return new BalanceOperation(true, amount, balance, "");
    }

    public static BalanceOperation failure(BigDecimal amount, BigDecimal balance, String message) {
        return new BalanceOperation(false, amount, balance, message);
    }
}
