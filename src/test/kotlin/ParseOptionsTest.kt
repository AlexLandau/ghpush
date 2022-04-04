package com.github.alexlandau.ghpush

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ParseOptionsTest {
    @Test
    fun testOntoWithEquals() {
        assertThat(fromArgs("--onto=release/1.2.3"))
            .isEqualTo(fromArgs().copy(onto = "release/1.2.3"))
    }

    @Test
    fun testOntoWithoutEquals() {
        assertThat(fromArgs("--onto", "release/1.2.3"))
            .isEqualTo(fromArgs().copy(onto = "release/1.2.3"))
    }

    @Test
    fun testOntoAtEnd() {
        assertThat(fromArgs("--onto").errors).isNotEmpty()
        assertThat(fromArgs("--onto").onto).isNull()
    }

    @Test
    fun testUnknownArgument() {
        assertThat(fromArgs("-x"))
            .isEqualTo(fromArgs().copy(unrecognizedArgs = listOf("-x")))
    }
}