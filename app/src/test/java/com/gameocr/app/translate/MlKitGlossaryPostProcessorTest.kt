package com.gameocr.app.translate

import com.gameocr.app.glossary.GlossaryMatch
import com.gameocr.app.glossary.GlossaryTermCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class MlKitGlossaryPostProcessorTest {
    @Test
    fun protectAndRestore_replacesLongestTermsAndRestoresTargets() {
        val matches = listOf(
            GlossaryMatch("equation", "معادلة", GlossaryTermCategory.TERM, false),
            GlossaryMatch("inequalities", "المتباينات", GlossaryTermCategory.TERM, false),
        )

        val protected = MlKitGlossaryPostProcessor.protect(
            "Solve inequalities and equation.",
            matches,
        )
        assertEquals("Solve OT_GLOSSARY_0_X and OT_GLOSSARY_1_X.", protected.text)

        val translated = "حل OT_GLOSSARY_0_X و OT_GLOSSARY_1_X."
        assertEquals(
            "حل المتباينات و معادلة.",
            MlKitGlossaryPostProcessor.restore(translated, protected.replacements),
        )
    }

    @Test
    fun protect_doesNotReplaceTermsInsideLargerWords() {
        val protected = MlKitGlossaryPostProcessor.protect(
            "inequalities inequality",
            listOf(GlossaryMatch("inequality", "متباينة", GlossaryTermCategory.TERM, false)),
        )
        assertEquals("inequalities OT_GLOSSARY_0_X", protected.text)
    }
}
