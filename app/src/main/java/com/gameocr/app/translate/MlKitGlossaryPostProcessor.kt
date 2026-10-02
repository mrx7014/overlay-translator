package com.gameocr.app.translate

import com.gameocr.app.appcontext.ForegroundAppResolver
import com.gameocr.app.data.Settings
import com.gameocr.app.glossary.GlossaryMatch
import com.gameocr.app.glossary.TranslationGlossaryRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies the user glossary to ML Kit, which has no native terminology feature.
 *
 * Matched source terms are replaced with stable ASCII placeholders before the offline
 * translation request. The placeholders are restored with the glossary targets after ML Kit
 * returns, so a term such as "inequalities" is rendered as "المتباينات" regardless of ML Kit's
 * preferred wording.
 */
@Singleton
class MlKitGlossaryPostProcessor @Inject constructor(
    private val repository: TranslationGlossaryRepository,
    private val foregroundAppResolver: ForegroundAppResolver,
) {
    suspend fun translateWithGlossary(
        source: String,
        settings: Settings,
        translate: suspend (String) -> String,
    ): String {
        if (!settings.translationGlossaryEnabled) return translate(source)
        val packageName = settings.runtimeTranslationScopePackage ?: foregroundAppResolver
            .resolve(settings.foregroundAppDetectionMode)
            ?.packageName
        val matches = repository.matchingTerms(
            source = source,
            sourceLang = settings.sourceLang,
            targetLang = settings.targetLang,
            packageName = packageName?.takeIf(String::isNotBlank),
        )
        if (matches.isEmpty()) return translate(source)
        val protected = protect(source, matches)
        val translated = translate(protected.text)
        return restore(translated, protected.replacements)
    }

    internal data class Replacement(val token: String, val target: String)
    internal data class ProtectedText(val text: String, val replacements: List<Replacement>)

    internal companion object {
        fun protect(source: String, matches: List<GlossaryMatch>): ProtectedText {
            var result = source
            val replacements = mutableListOf<Replacement>()
            matches.sortedByDescending { it.sourceTerm.length }.forEachIndexed { index, match ->
                val token = "OT_GLOSSARY_${index}_X"
                val pattern = Regex(
                    "(?<![\\p{L}\\p{N}_])${Regex.escape(match.sourceTerm)}(?![\\p{L}\\p{N}_])",
                    setOf(RegexOption.IGNORE_CASE),
                )
                val updated = pattern.replace(result, token)
                if (updated != result) {
                    result = updated
                    replacements += Replacement(token, match.targetTerm)
                }
            }
            return ProtectedText(result, replacements)
        }

        fun restore(translated: String, replacements: List<Replacement>): String =
            replacements.fold(translated) { text, replacement ->
                text.replace(replacement.token, replacement.target, ignoreCase = true)
            }
    }
}
