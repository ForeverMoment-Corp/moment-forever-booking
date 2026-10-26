package com.forvmom.MomentForeverBooking.events;

import java.math.BigDecimal;
import java.time.Instant;

public class ReceiptEligibilityCapturedEvent extends BaseEvent implements OutboundEvent {

    private String bookingId;
    private Long userId;
    private String userEmail;
    private String userFullName;
    private String experienceName;
    private Instant bookingDate;
    private BigDecimal expectedAmount;
    private String expectedCurrency;
    private String paymentTransactionId;
    private BigDecimal amountPaid;
    private String currencyPaid;
    private Instant paidAt;

    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId(String bookingId) {
        this.bookingId = bookingId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getUserFullName() {
        return userFullName;
    }

    public void setUserFullName(String userFullName) {
        this.userFullName = userFullName;
    }

    public String getExperienceName() {
        return experienceName;
    }

    public void setExperienceName(String experienceName) {
        this.experienceName = experienceName;
    }

    public Instant getBookingDate() {
        return bookingDate;
    }

    public void setBookingDate(Instant bookingDate) {
        this.bookingDate = bookingDate;
    }

    public BigDecimal getExpectedAmount() {
        return expectedAmount;
    }

    public void setExpectedAmount(BigDecimal expectedAmount) {
        this.expectedAmount = expectedAmount;
    }

    public String getExpectedCurrency() {
        return expectedCurrency;
    }

    public void setExpectedCurrency(String expectedCurrency) {
        this.expectedCurrency = expectedCurrency;
    }

    public String getPaymentTransactionId() {
        return paymentTransactionId;
    }

    public void setPaymentTransactionId(String paymentTransactionId) {
        this.paymentTransactionId = paymentTransactionId;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public void setAmountPaid(BigDecimal amountPaid) {
        this.amountPaid = amountPaid;
    }

    public String getCurrencyPaid() {
        return currencyPaid;
    }

    public void setCurrencyPaid(String currencyPaid) {
        this.currencyPaid = currencyPaid;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Instant paidAt) {
        this.paidAt = paidAt;
    }
}
