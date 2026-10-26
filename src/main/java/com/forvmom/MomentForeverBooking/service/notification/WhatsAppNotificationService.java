package com.forvmom.MomentForeverBooking.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class WhatsAppNotificationService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppNotificationService.class);

    private final RestTemplate restTemplate;

    @Value("${app.notifications.whatsapp.enabled:false}")
    private boolean enabled;

    @Value("${app.notifications.whatsapp.endpoint:}")
    private String endpoint;

    @Value("${app.notifications.whatsapp.authorization-token:}")
    private String authorizationToken;

    public WhatsAppNotificationService() {
        this.restTemplate = new RestTemplate();
    }

    public ChannelDeliveryOutcome sendLifecycleMessage(
            String eventType,
            String bookingId,
            String recipient,
            String message) {
        if (!enabled) {
            log.debug("WhatsApp lifecycle notification skipped: bookingId={}, eventType={}, reason=channel_disabled",
                    bookingId, eventType);
            return ChannelDeliveryOutcome.SKIPPED;
        }
        if (!isValidRecipient(recipient)) {
            log.warn("WhatsApp lifecycle notification skipped: bookingId={}, eventType={}, reason=invalid_recipient",
                    bookingId, eventType);
            return ChannelDeliveryOutcome.SKIPPED;
        }
        if (!StringUtils.hasText(endpoint) || !StringUtils.hasText(authorizationToken)) {
            log.error("WhatsApp lifecycle notification skipped: bookingId={}, eventType={}, reason=channel_not_configured",
                    bookingId, eventType);
            return ChannelDeliveryOutcome.SKIPPED;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(authorizationToken);
        try {
            restTemplate.postForEntity(
                    endpoint,
                    new HttpEntity<>(Map.of("to", recipient, "message", message), headers),
                    Void.class);
            log.info("WhatsApp lifecycle notification sent: bookingId={}, eventType={}", bookingId, eventType);
            return ChannelDeliveryOutcome.SENT;
        } catch (RestClientException exception) {
            log.warn("WhatsApp lifecycle notification failed: bookingId={}, eventType={}, exceptionType={}",
                    bookingId, eventType, exception.getClass().getSimpleName());
            throw new TransientNotificationException("WhatsApp provider request failed", exception);
        }
    }

    private boolean isValidRecipient(String recipient) {
        return StringUtils.hasText(recipient) && recipient.length() <= 254;
    }
}
