package com.earthpol.kernel.data.model;

public record MailRecord(long id, String senderName, String message, long sentEpochMillis) {
}
