package com.earthpol.kernel.data.model;

import java.math.BigDecimal;

public record BalanceTransfer(
    boolean success,
    BigDecimal amount,
    BigDecimal senderBalance,
    BigDecimal recipientBalance,
    String message
) {

    public static BalanceTransfer success(BigDecimal amount, BigDecimal senderBalance, BigDecimal recipientBalance) {
        return new BalanceTransfer(true, amount, senderBalance, recipientBalance, "");
    }

    public static BalanceTransfer failure(
        BigDecimal amount,
        BigDecimal senderBalance,
        BigDecimal recipientBalance,
        String message
    ) {
        return new BalanceTransfer(false, amount, senderBalance, recipientBalance, message);
    }
}
