package com.ticketing.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashUtilsTest {

    @Test
    void sameInputGivesSameHash() {
        assertThat(HashUtils.sha256("seats=2")).isEqualTo(HashUtils.sha256("seats=2"));
    }

    @Test
    void differentInputGivesDifferentHash() {
        assertThat(HashUtils.sha256("seats=2")).isNotEqualTo(HashUtils.sha256("seats=3"));
    }
}
