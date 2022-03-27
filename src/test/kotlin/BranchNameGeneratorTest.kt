package com.github.alexlandau.ghpush

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BranchNameGeneratorTest {
    @Test
    fun testBranchNameAutogenerator() {
        val generate = { title: String -> autogenerateBranchName(title, null, { false }) }
        assertThat(generate("This is an example commit title.")).isEqualTo("this-is-an-example-commit-title")
        assertThat(generate("[improvement] //, /* */, and # support")).isEqualTo("improvement-and-support")
        assertThat(generate("0123456789012345678901234567890123456789toolong")).isEqualTo("0123456789012345678901234567890123456789")
        assertThat(generate("This is an example, particularly long commit title.")).isEqualTo("this-is-an-example-particularly-long-com")
        // TODO: assertThat(generate("Preserve version number 1.2.3")).isEqualTo("preserve-version-number-1.2.3")
        // TODO: assertThat(generate("Preserve underscores some_python_fn()")).isEqualTo("preserve-underscores-some_python_fn")
    }

    @Test
    fun testIsValidGhBranchName() {
        // TODO: I haven't actually looked up the actual GH branch name requirements
        assertTrue(isValidGhBranchName("ok"))
        assertTrue(isValidGhBranchName("just-some-normal-name"))
        assertTrue(isValidGhBranchName("username/just-some-normal-name"))
        assertTrue(isValidGhBranchName("username/category/still-a-normal-name"))
        assertTrue(isValidGhBranchName("hyphenated-category/still-a-normal-name"))

//        assertFalse(isValidGhBranchName("username/category/but-this-one-is-just-way-too-long-unfortunately"))
        assertFalse(isValidGhBranchName("-hyphen-at-start"))
        assertFalse(isValidGhBranchName("hyphen-at-end-"))
        assertFalse(isValidGhBranchName("double--hyphens"))
        assertFalse(isValidGhBranchName(""))
        assertFalse(isValidGhBranchName("/starts-with-a-slash"))
        assertFalse(isValidGhBranchName("ends-with-a-slash/"))
        assertFalse(isValidGhBranchName("double//slash"))
        assertFalse(isValidGhBranchName("slash/-and-hyphen"))
        assertFalse(isValidGhBranchName("hyphen-/and-slash"))
        assertFalse(isValidGhBranchName("bad character"))
    }
}
