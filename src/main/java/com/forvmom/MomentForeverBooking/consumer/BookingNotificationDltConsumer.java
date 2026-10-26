package com.forvmom.MomentForeverBooking.consumer;

import com.forvmom.MomentForeverBooking.service.alerts.AlertService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class BookingNotificationDltConsumer {

    private final AlertService alertService;

    public BookingNotificationDltConsumer(AlertService alertService) {
        this.alertService = alertService;
    }

    @KafkaListener(
            topics = {
                    "${kafka.topics.booking-confirmed-dlt}",
                    "${kafka.topics.booking-cancelled-dlt}",
                    "${kafka.topics.receipt-delivery-dlt}"
            },
            groupId = "booking-notification-dlt-alert-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void onNotificationDeadLetter(ConsumerRecord<String, Object> record, Acknowledgment acknowledgment) {
        alertService.sendAlert("Notification DLT event requires operations review: topic="
                + record.topic() + ", partition=" + record.partition() + ", offset=" + record.offset());
        acknowledgment.acknowledge();
    }
}
