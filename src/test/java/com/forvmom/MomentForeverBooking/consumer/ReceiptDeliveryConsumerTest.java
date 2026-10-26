package com.forvmom.MomentForeverBooking.consumer;

import com.forvmom.MomentForeverBooking.events.ReceiptDeliveryRequestedEvent;
import com.forvmom.MomentForeverBooking.service.notification.BillingDocumentClient;
import com.forvmom.MomentForeverBooking.service.notification.ChannelDeliveryOutcome;
import com.forvmom.MomentForeverBooking.service.notification.EmailNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReceiptDeliveryConsumerTest {

    @Test
    void nonResendDeliveryIsAcknowledgedWithoutDownloadingOrEmailing() {
        BillingDocumentClient documentClient = mock(BillingDocumentClient.class);
        EmailNotificationService emailService = mock(EmailNotificationService.class);
        ReceiptDeliveryConsumer consumer = new ReceiptDeliveryConsumer(documentClient, emailService);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);

        consumer.onReceiptDeliveryRequested(deliveryEvent(false), acknowledgment);

        verify(acknowledgment).acknowledge();
        verifyNoInteractions(documentClient, emailService);
    }

    @Test
    void explicitResendDownloadsAndEmailsTheReceipt() {
        BillingDocumentClient documentClient = mock(BillingDocumentClient.class);
        EmailNotificationService emailService = mock(EmailNotificationService.class);
        ReceiptDeliveryConsumer consumer = new ReceiptDeliveryConsumer(documentClient, emailService);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        ReceiptDeliveryRequestedEvent event = deliveryEvent(true);
        byte[] pdf = new byte[]{1, 2, 3};
        when(documentClient.downloadImmutableReceipt(event.getDocumentId())).thenReturn(pdf);
        when(emailService.sendReceiptEmail(
                event.getBookingId(),
                event.getReceiptNumber(),
                event.getRecipientEmail(),
                pdf,
                true)).thenReturn(ChannelDeliveryOutcome.SENT);

        consumer.onReceiptDeliveryRequested(event, acknowledgment);

        verify(documentClient).downloadImmutableReceipt(event.getDocumentId());
        verify(emailService).sendReceiptEmail(
                event.getBookingId(),
                event.getReceiptNumber(),
                event.getRecipientEmail(),
                pdf,
                true);
        verify(acknowledgment).acknowledge();
        verify(emailService, never()).sendReceiptEmail(
                event.getBookingId(),
                event.getReceiptNumber(),
                event.getRecipientEmail(),
                pdf,
                false);
    }

    private ReceiptDeliveryRequestedEvent deliveryEvent(boolean resend) {
        ReceiptDeliveryRequestedEvent event = new ReceiptDeliveryRequestedEvent();
        event.setBookingId("booking-1");
        event.setDocumentId("receipt-1");
        event.setReceiptNumber("MF-R-FY2026-27-000001");
        event.setRecipientEmail("customer@example.com");
        event.setResend(resend);
        return event;
    }
}
