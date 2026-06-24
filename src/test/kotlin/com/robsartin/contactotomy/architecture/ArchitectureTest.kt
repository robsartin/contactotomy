package com.robsartin.contactotomy.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertTrue
import kotlin.test.Test

class ArchitectureTest {
    @Test
    fun `core does not depend on ui or compose`() {
        Konsist
            .scopeFromProject()
            .files
            .filter { it.packagee?.name?.startsWith("com.robsartin.contactotomy.core") == true }
            .assertTrue { file ->
                file.imports.none { import ->
                    import.name.startsWith("androidx.compose") ||
                        import.name.startsWith("com.robsartin.contactotomy.ui")
                }
            }
    }
}
