package com.forvmom.MomentForeverBooking.service.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Component
public class BillingDocumentClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${billing.internal.base-url:}")
    private String billingBaseUrl;

    @Value("${billing.internal.token:}")
    private String internalToken;

    public byte[] downloadImmutableReceipt(String documentId) {
        if (!StringUtils.hasText(billingBaseUrl) || !StringUtils.hasText(internalToken)) {
            throw new NonRetryableReceiptDeliveryException("Billing internal retrieval is not configured");
        }
        if (!StringUtils.hasText(documentId)) {
            throw new NonRetryableReceiptDeliveryException("Receipt document id is missing");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Billing-Internal-Token", internalToken);
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    billingBaseUrl + "/internal/receipts/{documentId}/pdf",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    byte[].class,
                    documentId);
            if (!response.getStatusCode().is2xxSuccessful()
                    || response.getBody() == null
                    || response.getBody().length == 0) {
                throw new NonRetryableReceiptDeliveryException("Billing returned no receipt PDF");
            }
            return response.getBody();
        } catch (HttpClientErrorException exception) {
            throw new NonRetryableReceiptDeliveryException("Billing rejected immutable receipt retrieval", exception);
        } catch (HttpServerErrorException | ResourceAccessException exception) {
            throw new TransientNotificationException("Billing receipt retrieval failed", exception);
        }
    }
}
