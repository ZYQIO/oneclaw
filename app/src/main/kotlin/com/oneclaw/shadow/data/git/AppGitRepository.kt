package com.oneclaw.shadow.data.git

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.EmptyCommitException
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.NullProgressMonitor
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.BundleWriter
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import org.eclipse.jgit.treewalk.TreeWalk
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class AppGitRepository(private val context: Context) {

    companion object {
        private const val TAG = "AppGitRepository"
        private val COMMITTER = PersonIdent("OneClaw Agent", "agent@oneclaw.local")
        private val GITIGNORE_CONTENT = """
            attachments/
            bridge_images/
            *.jpg
            *.jpeg
            *.png
            *.gif
            *.webp
            *.bmp
            *.mp4
            *.onnx
            *.pdf
            *.db
            *.db-shm
            *.db-wal
            *.bundle
            *.zip
            MEMORY_backup_*.md
        """.trimIndent()
    }

    val repoDir: File get() = context.filesDir
    private var git: Git? = null

    suspend fun initOrOpen(): Unit = withContext(Dispatchers.IO) {
        try {
            val gitDir = File(repoDir, ".git")
            if (!gitDir.exists()) {
                // Write .gitignore first
                File(repoDir, ".gitignore").writeText(GITIGNORE_CONTENT)
                val newGit = Git.init().setDirectory(repoDir).call()
                git = newGit
                // Stage all existing files and initial commit
                newGit.add().addFilepattern(".").call()
                try {
                    newGit.commit()
                        .setCommitter(COMMITTER)
                        .setAuthor(COMMITTER)
                        .setMessage("init: initialize memory repository")
                        .call()
                } catch (e: EmptyCommitException) {
                    // Nothing to commit on fresh install -- that's fine
                }
                Log.d(TAG, "Git repo initialized at ${repoDir.absolutePath}")
            } else {
                git = Git.open(repoDir)
                Log.d(TAG, "Git repo opened at ${repoDir.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init/open git repo", e)
        }
    }

    suspend fun commitFile(relativePath: String, message: String): Unit = withContext(Dispatchers.IO) {
        try {
            val g = git ?: return@withContext
            g.add().addFilepattern(relativePath).call()
            // Also stage deletions
            g.add().setUpdate(true).addFilepattern(relativePath).call()
            g.commit()
                .setCommitter(COMMITTER)
                .setAuthor(COMMITTER)
                .setMessage(message)
                .setAllowEmpty(false)
                .call()
        } catch (e: EmptyCommitException) {
            Log.d(TAG, "Nothing to commit for $relativePath")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to commit $relativePath: ${e.message}")
        }
    }

    suspend fun commit(message: String): Unit = withContext(Dispatchers.IO) {
        try {
            val g = git ?: return@withContext
            g.add().addFilepattern(".").call()
            g.add().setUpdate(true).addFilepattern(".").call()
            g.commit()
                .setCommitter(COMMITTER)
                .setAuthor(COMMITTER)
                .setMessage(message)
                .setAllowEmpty(false)
                .call()
        } catch (e: EmptyCommitException) {
            Log.d(TAG, "Nothing to commit")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to commit: ${e.message}")
        }
    }

    suspend fun log(path: String? = null, maxCount: Int = 50): List<GitCommitEntry> = withContext(Dispatchers.IO) {
        try {
            val g = git ?: return@withContext emptyList()
            val logCmd = g.log().setMaxCount(maxCount)
            if (path != null) logCmd.addPath(path)
            logCmd.call().map { rev ->
                GitCommitEntry(
                    sha = rev.name,
                    shortSha = rev.name.take(7),
                    message = rev.shortMessage,
                    authorTime = rev.authorIdent.`when`.time,
                    changedFiles = emptyList()
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "git log failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun show(sha: String): String = withContext(Dispatchers.IO) {
        try {
            val g = git ?: return@withContext "Repository not initialized"
            val repo = g.repository
            val revWalk = RevWalk(repo)
            val commit = revWalk.parseCommit(repo.resolve(sha))
            val out = ByteArrayOutputStream()
            val df = DiffFormatter(out)
            df.setRepository(repo)

            val reader = repo.newObjectReader()
            val newTree = CanonicalTreeParser().apply { reset(reader, commit.tree) }
            val oldTree = if (commit.parentCount > 0) {
                val parent = revWalk.parseCommit(commit.getParent(0))
                CanonicalTreeParser().apply { reset(reader, parent.tree) }
            } else {
                EmptyTreeIterator()
            }

            val diffs = g.diff()
                .setNewTree(newTree)
                .setOldTree(oldTree)
                .call()
            df.format(diffs)
            df.flush()

            buildString {
                appendLine("commit ${commit.name}")
                appendLine("Author: ${commit.authorIdent.name} <${commit.authorIdent.emailAddress}>")
                appendLine("Date:   ${commit.authorIdent.`when`}")
                appendLine()
                appendLine("    ${commit.fullMessage.trim()}")
                appendLine()
                append(out.toString(Charsets.UTF_8))
            }
        } catch (e: Exception) {
            "Error showing commit $sha: ${e.message}"
        }
    }

    suspend fun diff(fromSha: String, toSha: String?): String = withContext(Dispatchers.IO) {
        try {
            val g = git ?: return@withContext "Repository not initialized"
            val repo = g.repository
            val reader = repo.newObjectReader()
            val revWalk = RevWalk(repo)

            val fromCommit = revWalk.parseCommit(repo.resolve(fromSha))
            val oldTree = CanonicalTreeParser().apply { reset(reader, fromCommit.tree) }

            val newTree = if (toSha != null) {
                val toCommit = revWalk.parseCommit(repo.resolve(toSha))
                CanonicalTreeParser().apply { reset(reader, toCommit.tree) }
            } else {
                CanonicalTreeParser().apply {
                    val headTree = repo.resolve("HEAD^{tree}")
                    if (headTree != null) reset(reader, headTree)
                }
            }

            val out = ByteArrayOutputStream()
            val df = DiffFormatter(out)
            df.setRepository(repo)
            val diffs = g.diff().setOldTree(oldTree).setNewTree(newTree).call()
            df.format(diffs)
            df.flush()
            out.toString(Charsets.UTF_8).ifBlank { "No differences found." }
        } catch (e: Exception) {
            "Error computing diff: ${e.message}"
        }
    }

    suspend fun restore(relativePath: String, sha: String): Unit = withContext(Dispatchers.IO) {
        val g = git ?: throw IllegalStateException("Repository not initialized")
        val repo = g.repository
        val revWalk = RevWalk(repo)
        val commit = revWalk.parseCommit(repo.resolve(sha))
        val treeWalk = TreeWalk.forPath(repo, relativePath, commit.tree)
            ?: throw IllegalArgumentException("File $relativePath not found in commit $sha")
        val objectId = treeWalk.getObjectId(0)
        val loader = repo.open(objectId)
        val targetFile = File(repoDir, relativePath)
        targetFile.parentFile?.mkdirs()
        FileOutputStream(targetFile).use { loader.copyTo(it) }
    }

    suspend fun bundle(outputFile: File): Unit = withContext(Dispatchers.IO) {
        val g = git ?: throw IllegalStateException("Repository not initialized")
        outputFile.parentFile?.mkdirs()
        val headId = g.repository.resolve("HEAD")
            ?: throw IllegalStateException("Repository has no commits yet")
        val writer = BundleWriter(g.repository)
        writer.include("refs/heads/main", headId)
        outputFile.outputStream().use { out ->
            writer.writeBundle(NullProgressMonitor.INSTANCE, out)
        }
    }

    suspend fun gc(): Unit = withContext(Dispatchers.IO) {
        try {
            git?.gc()?.call()
        } catch (e: Exception) {
            Log.w(TAG, "git gc failed: ${e.message}")
        }
    }
}

data class GitCommitEntry(
    val sha: String,
    val shortSha: String,
    val message: String,
    val authorTime: Long,
    val changedFiles: List<String>
)
