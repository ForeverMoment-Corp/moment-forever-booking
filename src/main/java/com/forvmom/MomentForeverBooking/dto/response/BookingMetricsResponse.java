package com.forvmom.MomentForeverBooking.dto.response;


import java.math.BigDecimal;
import java.time.LocalDateTime;


/**
 * Dashboard KPI summary returned by {@code GET /admin/bookings/metrics}.
 *
 * <p>
 * PLACEHOLDER / KNOWN LIMITATION: {@link com.forvmom.MomentForeverBooking.domain.enums.BookingStatus}
 * has no EXPIRED value today (only PENDING/CONFIRMED/FAILED/CANCELLED), so
 * there is no "expired" count to report. Add it here once the domain model
 * gains an EXPIRED status (e.g. payment session timed out without a
 * webhook callback).
 */
public class BookingMetricsResponse {


    private LocalDateTime from;
    private LocalDateTime to;


    private long total;
    private long pending;
    private long confirmed;
    private long failed;
    private long cancelled;


    /** confirmed / total * 100, rounded to 2 decimal places. 0 when total is 0. */
    private BigDecimal conversionRatePercent;


    /** Average grandTotal across CONFIRMED bookings in range. Null when there are none. */
    private BigDecimal averageConfirmedBookingValue;


    public LocalDateTime getFrom() {
        return from;
    }


    public void setFrom(LocalDateTime from) {
        this.from = from;
    }


    public LocalDateTime getTo() {
        return to;
    }


    public void setTo(LocalDateTime to) {
        this.to = to;
    }


    public long getTotal() {
        return total;
    }


    public void setTotal(long total) {
        this.total = total;
    }


    public long getPending() {
        return pending;
    }


    public void setPending(long pending) {
        this.pending = pending;
    }


    public long getConfirmed() {
        return confirmed;
    }


    public void setConfirmed(long confirmed) {
        this.confirmed = confirmed;
    }


    public long getFailed() {
        return failed;
    }


    public void setFailed(long failed) {
        this.failed = failed;
    }


    public long getCancelled() {
        return cancelled;
    }


    public void setCancelled(long cancelled) {
        this.cancelled = cancelled;
    }


    public BigDecimal getConversionRatePercent() {
        return conversionRatePercent;
    }


    public void setConversionRatePercent(BigDecimal conversionRatePercent) {
        this.conversionRatePercent = conversionRatePercent;
    }


    public BigDecimal getAverageConfirmedBookingValue() {
        return averageConfirmedBookingValue;
    }


    public void setAverageConfirmedBookingValue(BigDecimal averageConfirmedBookingValue) {
        this.averageConfirmedBookingValue = averageConfirmedBookingValue;
    }
}



