import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.io.File

fun main(args: Array<String>) {
    val disposable = Disposer.newDisposable()
    try {
        val environment = KotlinCoreEnvironment.createForProduction(disposable, CompilerConfiguration(), EnvironmentConfigFiles.JVM_CONFIG_FILES)
        val factory = KtPsiFactory(environment.project)
        var failures = 0
        args.forEach { path ->
            val source = File(path)
            val text = com.intellij.openapi.util.text.StringUtil.convertLineSeparators(source.readText().removePrefix("\uFEFF"))
            val file = factory.createFile(source.name, text)
            PsiTreeUtil.collectElementsOfType(file, PsiErrorElement::class.java).forEach { error ->
                val line = text.take(error.textOffset).count { it == '\n' } + 1
                System.err.println("${source.path}:$line: ${error.errorDescription}")
                failures++
            }
        }
        check(failures == 0) { "$failures Kotlin syntax errors" }
        println("Kotlin syntax: ${args.size} files passed (not Android type checking)")
    } finally {
        Disposer.dispose(disposable)
    }
}