package com.tfzhang.linuxback.service.impl;

import com.tfzhang.linuxback.entity.Dispute;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DisputeStatusTest {

    @Test
    void derive_nullIsZero() {
        assertEquals(0, DisputeStatus.derive(null));
    }

    @Test
    void derive_openIsOne() {
        assertEquals(1, DisputeStatus.derive(new Dispute()));
    }

    @Test
    void derive_resolvedIsTwo() {
        Dispute d = new Dispute();
        d.setResolveTime(LocalDateTime.of(2026, 9, 8, 12, 0));
        assertEquals(2, DisputeStatus.derive(d));
    }

    @Test
    void derive_withdrawnIsThree() {
        Dispute d = new Dispute();
        d.setWithdrawnTime(LocalDateTime.of(2026, 9, 8, 9, 0));
        assertEquals(3, DisputeStatus.derive(d));
    }

    @Test
    void derive_bothTimesSet_teacherCloseWins() {
        Dispute d = new Dispute();
        d.setResolveTime(LocalDateTime.of(2026, 9, 8, 12, 0));
        d.setWithdrawnTime(LocalDateTime.of(2026, 9, 8, 9, 0));
        assertEquals(2, DisputeStatus.derive(d));
    }
}
