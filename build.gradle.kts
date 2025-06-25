import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

abstract class GitExecutor @Inject constructor(
    private val execOperations: ExecOperations
) {
    fun execute(vararg args: String): String {
        val output = ByteArrayOutputStream()
        execOperations.exec {
            commandLine("git", *args)
            standardOutput = output
            isIgnoreExitValue = true
        }
        return output.toString().trim()
    }
}

if (project.version.toString().endsWith("-SNAPSHOT")) {
    val git = objects.newInstance(GitExecutor::class.java)
    val baseTag = git.execute("describe", "--tags", "--abbrev=0")
    val commitCount = git.execute("rev-list", "--count", "$baseTag..HEAD")
    val abbrevHash = git.execute("rev-parse", "--short", "HEAD")
    // Replace the `-SNAPSHOT` pre-release identifier with information to identify the build.
    project.version = project.version.toString().replace("-SNAPSHOT", "-dev+$commitCount-$abbrevHash")
}

allprojects {
    // Copy the final version name to subprojects.
    version = rootProject.version
}
