package com.forvmom.MomentForeverBooking.service.inbound;

import com.forvmom.MomentForeverBooking.domain.entity.OutgoingOutboxRecord;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public record OutboundProcessingResult(List<OutgoingOutboxRecord> records) {

    public OutboundProcessingResult {
        records = records == null ? List.of() : records.stream()
                .filter(Objects::nonNull)
                .toList();
    }

    public static OutboundProcessingResult none() {
        return new OutboundProcessingResult(List.of());
    }

    public static OutboundProcessingResult of(OutgoingOutboxRecord... records) {
        return new OutboundProcessingResult(Arrays.stream(records)
                .filter(Objects::nonNull)
                .toList());
    }

    public OutgoingOutboxRecord firstRecordOrNull() {
        return records.isEmpty() ? null : records.get(0);
    }
}
