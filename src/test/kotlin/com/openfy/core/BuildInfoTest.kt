package com.openfy.core

import org.junit.Assert.assertTrue
import org.junit.Test

/** A versao do build precisa continuar em formato semver MAJOR.MINOR.PATCH. */
class BuildInfoTest {

    @Test
    fun `VERSION segue formato semver numerico`() {
        assertTrue(
            "BuildInfo.VERSION='${BuildInfo.VERSION}' nao e um semver MAJOR.MINOR.PATCH",
            BuildInfo.VERSION.matches(Regex("""\d+\.\d+\.\d+""")),
        )
    }

    @Test
    fun `VERSION nao e vazia`() {
        assertTrue(BuildInfo.VERSION.isNotBlank())
    }
}
