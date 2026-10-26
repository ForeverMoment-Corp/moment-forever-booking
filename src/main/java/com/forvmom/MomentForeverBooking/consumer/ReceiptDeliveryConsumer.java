package com.forvmom.MomentForeverBooking.consumer;

import com.forvmom.MomentForeverBooking.events.ReceiptDeliveryRequestedEvent;
import com.forvmom.MomentForeverBooking.service.notification.BillingDocumentClient;
import com.forvmom.MomentForeverBooking.service.notification.ChannelDeliveryOutcome;
import com.forvmom.MomentForeverBooking.service.notification.EmailNotificationService;
import com.forvmom.MomentForeverBooking.service.notification.NonRetryableReceiptDeliveryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class ReceiptDeliveryConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReceiptDeliveryConsumer.class);

    private final BillingDocumentClient billingDocumentClient;
    private final EmailNotificationService emailNotificationService;

    public ReceiptDeliveryConsumer(
            BillingDocumentClient billingDocumentClient,
            EmailNotificationService emailNotificationService) {
        this.billingDocumentClient = billingDocumentClient;
        this.emailNotificationService = emailNotificationService;
    }

    @KafkaListener(
            topics = "${kafka.topics.receipt-delivery-requested}",
            groupId = "booking-receipt-delivery-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void onReceiptDeliveryRequested(
            @Payload ReceiptDeliveryRequestedEvent event,
            Acknowledgment acknowledgment) {
        if (!event.isResend()) {
            log.error("Receipt delivery event rejected because it is not an explicit resend: bookingId={}, documentId={}",
                    event.getBookingId(), event.getDocumentId());
            acknowledgment.acknowledge();
            return;
        }
        try {
            byte[] pdf = billingDocumentClient.downloadImmutableReceipt(event.getDocumentId());
            ChannelDeliveryOutcome outcome = emailNotificationService.sendReceiptEmail(
                    event.getBookingId(),
                    event.getReceiptNumber(),
                    event.getRecipientEmail(),
                    pdf,
                    event.isResend());
            if (outcome == ChannelDeliveryOutcome.SKIPPED) {
                log.warn("Receipt delivery skipped without retry: bookingId={}, documentId={}",
                        event.getBookingId(), event.getDocumentId());
            }
            acknowledgment.acknowledge();
        } catch (NonRetryableReceiptDeliveryException exception) {
            log.error("Receipt delivery skipped without retry: bookingId={}, documentId={}, reason={}",
                    event.getBookingId(), event.getDocumentId(), exception.getMessage());
            acknowledgment.acknowledge();
        }
    }
}
