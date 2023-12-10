package bitlap.sbt.analyzer.jbexternal

import com.intellij.DynamicBundle
import org.jetbrains.annotations.*

class SbtDependencyExternalBundle : DynamicBundle(BUNDLE) {
    companion object {
        const val BUNDLE = "messages.SbtPluginExternalBundle"
        private val INSTANCE = SbtDependencyExternalBundle()

        @Nls
        fun message(@NotNull @PropertyKey(resourceBundle = BUNDLE) key: String, @NotNull vararg params: Any): String =
            INSTANCE.getMessage(key, params)
    }
}