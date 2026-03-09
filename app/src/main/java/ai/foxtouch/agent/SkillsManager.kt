package ai.foxtouch.agent

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class SkillSummary(val id: String, val title: String, val preview: String)

/**
 * Manages persistent skill files for the AI agent.
 * Skills are approved plans saved for reuse across sessions.
 * Each skill is a markdown file: first line is `# Title`, rest is plan content.
 */
@Singleton
class SkillsManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {
    private val skillsDir: File
        get() = File(appContext.filesDir, "agent_docs/skills").also { it.mkdirs() }

    fun listSkills(): List<SkillSummary> {
        val dir = skillsDir
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f -> f.extension == "md" }
            ?.mapNotNull { file ->
                val lines = file.readText().lines()
                val title = lines.firstOrNull()
                    ?.removePrefix("# ")
                    ?.trim()
                    ?: return@mapNotNull null
                val preview = lines.drop(1)
                    .firstOrNull { it.isNotBlank() }
                    ?.take(100)
                    ?: ""
                SkillSummary(
                    id = file.nameWithoutExtension,
                    title = title,
                    preview = preview,
                )
            }
            ?.sortedBy { it.title }
            ?: emptyList()
    }

    fun readSkill(id: String): String? {
        val file = File(skillsDir, "$id.md")
        return if (file.exists()) file.readText() else null
    }

    fun saveSkill(title: String, content: String): String {
        val id = UUID.randomUUID().toString().take(8)
        val fullContent = if (content.startsWith("# ")) {
            content
        } else {
            "# $title\n\n$content"
        }
        File(skillsDir, "$id.md").writeText(fullContent)
        return id
    }

    fun deleteSkill(id: String): Boolean {
        val file = File(skillsDir, "$id.md")
        return file.delete()
    }
}
