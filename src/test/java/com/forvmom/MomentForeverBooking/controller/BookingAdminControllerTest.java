package com.forvmom.MomentForeverBooking.controller;

import com.forvmom.MomentForeverBooking.domain.entity.OutgoingOutboxRecord;
import com.forvmom.MomentForeverBooking.exception.BookingAccessDeniedException;
import com.forvmom.MomentForeverBooking.service.BookingService;
import com.forvmom.MomentForeverBooking.service.retries_cleanup.OutgoingOutboxPublisher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingAdminControllerTest {

    @Test
    void completionRequiresAnOperationsRole() {
        BookingService bookingService = mock(BookingService.class);
        OutgoingOutboxPublisher publisher = mock(OutgoingOutboxPublisher.class);
        BookingAdminController controller = new BookingAdminController(bookingService, publisher);

        assertThrows(BookingAccessDeniedException.class,
                () -> controller.completeBooking("booking-1", "USER"));

        verify(bookingService, never()).completeBooking("booking-1");
    }

    @Test
    void adminCompletionPublishesOnlyAfterServiceStagesOutboxRecord() {
        BookingService bookingService = mock(BookingService.class);
        OutgoingOutboxPublisher publisher = mock(OutgoingOutboxPublisher.class);
        BookingAdminController controller = new BookingAdminController(bookingService, publisher);
        OutgoingOutboxRecord record = new OutgoingOutboxRecord();
        when(bookingService.completeBooking("booking-1")).thenReturn(record);

        controller.completeBooking("booking-1", "ADMIN");

        verify(bookingService).completeBooking("booking-1");
        verify(publisher).trySinglePublish(record);
    }
}
