package com.forvmom.MomentForeverBooking.producer;

import com.forvmom.MomentForeverBooking.events.BookingCancelledEvent;
import com.forvmom.MomentForeverBooking.events.BookingConfirmedEvent;
import com.forvmom.MomentForeverBooking.events.BookingFailedEvent;
import com.forvmom.MomentForeverBooking.events.ExperienceCompletedEvent;
import com.forvmom.MomentForeverBooking.events.PaymentRequestedEvent;
import com.forvmom.MomentForeverBooking.events.ReceiptEligibilityCapturedEvent;
import com.forvmom.MomentForeverBooking.events.ReceiptGenerationRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class BookingEventProducer {

    private static final Logger log = LoggerFactory.getLogger(BookingEventProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.booking-confirmed}")
    private String bookingConfirmedTopic;

    @Value("${kafka.topics.booking-failed}")
    private String bookingFailedTopic;

    @Value("${kafka.topics.booking-cancelled}")
    private String bookingCancelledTopic;

    @Value("${kafka.topics.payment-requested}")
    private String paymentRequestedTopic;

    @Value("${kafka.topics.receipt-generation-requested}")
    private String receiptGenerationRequestedTopic;

    @Value("${kafka.topics.receipt-eligibility-captured}")
    private String receiptEligibilityCapturedTopic;

    @Value("${kafka.topics.experience-completed}")
    private String experienceCompletedTopic;

    public BookingEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendBookingConfirmedEvent(BookingConfirmedEvent event) throws ExecutionException, InterruptedException, TimeoutException {
        kafkaTemplate.send(bookingConfirmedTopic, event.getBookingId(), event).get(5, TimeUnit.SECONDS);
        log.info("Sent BookingConfirmedEvent: bookingId={}, eventId={}, correlationId={}",
                event.getBookingId(), event.getEventId(), event.getCorrelationId());
    }

    public void sendBookingFailedEvent(BookingFailedEvent event) throws ExecutionException, InterruptedException, TimeoutException {
        kafkaTemplate.send(bookingFailedTopic, event.getBookingId(), event).get(5, TimeUnit.SECONDS);
        log.info("Sent BookingFailedEvent: bookingId={}, eventId={}, correlationId={}",
                event.getBookingId(), event.getEventId(), event.getCorrelationId());
    }

    public void sendBookingCancelledEvent(BookingCancelledEvent event) throws ExecutionException, InterruptedException, TimeoutException {
        kafkaTemplate.send(bookingCancelledTopic, event.getBookingId(), event).get(5, TimeUnit.SECONDS);
        log.info("Sent BookingCancelledEvent: bookingId={}, eventId={}, correlationId={}",
                event.getBookingId(), event.getEventId(), event.getCorrelationId());
    }

    public void sendPaymentRequestedEvent(PaymentRequestedEvent event) throws ExecutionException, InterruptedException, TimeoutException {
        kafkaTemplate.send(paymentRequestedTopic, event.getBookingId(), event).get(5, TimeUnit.SECONDS);
        log.info("Sent PaymentRequestedEvent: bookingId={}, eventId={}, correlationId={}",
                event.getBookingId(), event.getEventId(), event.getCorrelationId());
    }

    public void sendReceiptGenerationRequestedEvent(ReceiptGenerationRequestedEvent event)
            throws ExecutionException, InterruptedException, TimeoutException {
        kafkaTemplate.send(receiptGenerationRequestedTopic, event.getBookingId(), event).get(5, TimeUnit.SECONDS);
        log.info("Sent ReceiptGenerationRequestedEvent: bookingId={}, eventId={}, correlationId={}",
                event.getBookingId(), event.getEventId(), event.getCorrelationId());
    }

    public void sendReceiptEligibilityCapturedEvent(ReceiptEligibilityCapturedEvent event)
            throws ExecutionException, InterruptedException, TimeoutException {
        kafkaTemplate.send(receiptEligibilityCapturedTopic, event.getBookingId(), event).get(5, TimeUnit.SECONDS);
        log.info("Sent ReceiptEligibilityCapturedEvent: bookingId={}, eventId={}, correlationId={}",
                event.getBookingId(), event.getEventId(), event.getCorrelationId());
    }

    public void sendExperienceCompletedEvent(ExperienceCompletedEvent event)
            throws ExecutionException, InterruptedException, TimeoutException {
        kafkaTemplate.send(experienceCompletedTopic, event.getBookingId(), event).get(5, TimeUnit.SECONDS);
        log.info("Sent ExperienceCompletedEvent: bookingId={}, eventId={}, correlationId={}",
                event.getBookingId(), event.getEventId(), event.getCorrelationId());
    }
}