package app.orcaandroid.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * The on-device file layout the native core expects:
 *
 *   filesDir/resources      extracted resources.zip (resources_dir)
 *   filesDir/data           data_dir; data/system/<Vendor>.json + <Vendor>/ are installed vendors
 *   filesDir/projects       projects saved by the user
 *   cacheDir/models         imported model files (original names: the core picks the loader by extension)
 *   cacheDir/out            scene mesh, previews and G-code
 */
class ResourceStore(private val context: Context) {

    val resourcesDir = File(context.filesDir, "resources")
    val dataDir = File(context.filesDir, "data")
    val projectsDir = File(context.filesDir, "projects")
    private val systemDir = File(dataDir, "system")
    private val importDir = File(context.cacheDir, "models")

    /** Extracts bundled resources on first start or after an app update. */
    suspend fun prepare() = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("orca", Context.MODE_PRIVATE)
        val version = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        if (prefs.getLong("resources_version", -1) != version || !resourcesDir.isDirectory) {
            resourcesDir.deleteRecursively()
            context.assets.open("resources.zip").use { unzip(it, resourcesDir) }
            // Refresh installed vendors from the APK, unless an online update is newer.
            val bundled = availableVendors().associateBy { it.id }
            for (id in installedVendorIds()) {
                val b = bundled[id] ?: continue
                if (compareVersions(b.version, installedVersion(id)) >= 0) extractVendor(id)
            }
            prefs.edit().putLong("resources_version", version).apply()
        }
        listOf(dataDir, projectsDir, importDir).forEach { it.mkdirs() }
    }

    fun availableVendors(): List<Vendor> {
        val json = context.assets.open("vendors/index.json").bufferedReader().use { it.readText() }
        return JSONArray(json).map {
            val o = it as JSONObject
            val models = o.getJSONArray("models").map { m ->
                val model = m as JSONObject
                PrinterModel(model.getString("name"), model.getJSONArray("nozzles").map { n -> n as String })
            }
            Vendor(o.getString("id"), o.getString("name"), models, o.optBoolean("required"), o.optString("version"))
        }
    }

    fun installedVendorIds(): Set<String> =
        systemDir.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.map { it.nameWithoutExtension }?.toSet() ?: emptySet()

    private fun installedVersion(id: String): String =
        runCatching { JSONObject(File(systemDir, "$id.json").readText()).optString("version") }.getOrDefault("")

    /** Installs exactly [vendorIds] (plus the always-required filament library). */
    suspend fun setInstalledVendors(vendorIds: Set<String>) = withContext(Dispatchers.IO) {
        val wanted = vendorIds + availableVendors().filter { it.required }.map { it.id }
        systemDir.mkdirs()
        for (id in installedVendorIds() - wanted) {
            File(systemDir, "$id.json").delete()
            File(systemDir, id).deleteRecursively()
        }
        for (id in wanted - installedVendorIds()) extractVendor(id)
    }

    private fun extractVendor(id: String) {
        File(systemDir, id).deleteRecursively()
        context.assets.open("vendors/$id.zip").use { unzip(it, systemDir) }
    }

    // --- Online profile updates --------------------------------------------------------------------

    data class ProfileUpdate(val vendor: String, val installed: String, val available: String)

    /** Vendors whose profiles in the OrcaSlicer repository are newer than the installed ones. */
    suspend fun checkProfileUpdates(): List<ProfileUpdate> = withContext(Dispatchers.IO) {
        installedVendorIds().map { id ->
            async {
                val remote = runCatching { JSONObject(fetch("$RAW/resources/profiles/${enc(id)}.json")).optString("version") }.getOrNull()
                    ?: return@async null
                val local = installedVersion(id)
                if (compareVersions(remote, local) > 0) ProfileUpdate(id, local, remote) else null
            }
        }.awaitAll().filterNotNull()
    }

    /**
     * Brings the vendor's profiles up to date with the OrcaSlicer repository. GitHub lists every
     * file with its git blob hash, so only files that differ from the installed ones are
     * downloaded (usually a few, out of thousands for some vendors), several at a time.
     */
    suspend fun updateVendor(id: String, onProgress: (Int, Int) -> Unit) = withContext(Dispatchers.IO) {
        val listing = JSONArray(fetch("$API/contents/resources/profiles?ref=$BRANCH"))
        val dirSha = listing.map { it as JSONObject }.firstOrNull { it.getString("name") == id && it.getString("type") == "dir" }
            ?.getString("sha") ?: throw OrcaException("No profiles for $id in the OrcaSlicer repository")
        val tree = JSONObject(fetch("$API/git/trees/$dirSha?recursive=1"))
        if (tree.optBoolean("truncated")) throw OrcaException("The profile list of $id is too large to compare")
        val remote = tree.getJSONArray("tree").map { it as JSONObject }.filter { it.getString("type") == "blob" }
            .associate { it.getString("path") to it.getString("sha") }

        // Work on a copy of the installed profiles; swap it in only after everything arrived.
        val staging = File(context.cacheDir, "profile_update/$id").apply { deleteRecursively(); mkdirs() }
        File(systemDir, id).takeIf { it.isDirectory }?.copyRecursively(staging, overwrite = true)
        staging.walkBottomUp().filter { it.isFile && it.relativeTo(staging).invariantSeparatorsPath !in remote }.forEach { it.delete() }
        val changed = remote.filter { (path, sha) -> File(staging, path).let { !it.isFile || gitBlobSha(it) != sha } }.keys.toList()

        val done = AtomicInteger()
        onProgress(0, changed.size)
        coroutineScope {
            val gate = Semaphore(PARALLEL_DOWNLOADS)
            changed.map { path ->
                async {
                    gate.withPermit {
                        val target = File(staging, path).apply { parentFile?.mkdirs() }
                        download("$RAW/resources/profiles/${enc(id)}/${path.split('/').joinToString("/") { enc(it) }}", target)
                        onProgress(done.incrementAndGet(), changed.size)
                    }
                }
            }.awaitAll()
        }
        val index = File(context.cacheDir, "profile_update/$id.json")
        download("$RAW/resources/profiles/${enc(id)}.json", index)

        File(systemDir, id).deleteRecursively()
        staging.copyRecursively(File(systemDir, id), overwrite = true)
        index.copyTo(File(systemDir, "$id.json"), overwrite = true)
        staging.deleteRecursively()
        index.delete()
    }

    // --- Files ---------------------------------------------------------------------------------------

    /** Copies a picked document into the cache, keeping its name so the core can tell its type. */
    suspend fun importFile(uri: Uri, subdir: String = ""): File = withContext(Dispatchers.IO) {
        val dir = File(importDir, subdir).apply { mkdirs() }
        val file = File(dir, displayName(uri))
        context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        file
    }

    fun displayName(uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.let { return it.replace('/', '_') }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "model.stl"
    }

    /** Bundled sample models (resources/handy_models). */
    fun sampleModels(): List<File> = File(resourcesDir, "handy_models").listFiles()?.sortedBy { it.name }.orEmpty()

    /** A font for embossed text: the system's default sans font. */
    fun defaultFont(): String = listOf("/system/fonts/Roboto-Regular.ttf", "/system/fonts/RobotoStatic-Regular.ttf", "/system/fonts/DroidSans.ttf")
        .firstOrNull { File(it).exists() } ?: "/system/fonts/Roboto-Regular.ttf"

    private fun unzip(input: InputStream, target: File) {
        val root = target.canonicalFile
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val out = File(root, entry.name).canonicalFile
                require(out.path.startsWith(root.path + File.separator)) { "Bad zip entry ${entry.name}" }
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zip.copyTo(it) }
                }
            }
        }
    }

    private companion object {
        const val REPO = "SoftFever/OrcaSlicer"
        const val BRANCH = "main"
        const val RAW = "https://raw.githubusercontent.com/$REPO/$BRANCH"
        const val API = "https://api.github.com/repos/$REPO"
        const val PARALLEL_DOWNLOADS = 8

        fun gitBlobSha(file: File): String = gitBlobSha(file.readBytes())

        fun enc(s: String) = Uri.encode(s)

        fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "Orca-Android")
        }

        fun fetch(url: String): String {
            val c = open(url)
            try {
                if (c.responseCode != 200) throw OrcaException("HTTP ${c.responseCode}: $url")
                return c.inputStream.bufferedReader().use { it.readText() }
            } finally {
                c.disconnect()
            }
        }

        fun download(url: String, target: File) {
            val c = open(url)
            try {
                if (c.responseCode != 200) throw OrcaException("HTTP ${c.responseCode}: $url")
                c.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
            } finally {
                c.disconnect()
            }
        }

        /** Compares dotted version strings numerically ("02.03.00.10" vs "2.3.0.9"). */
        fun compareVersions(a: String, b: String): Int {
            val pa = a.split('.').map { it.toIntOrNull() ?: 0 }
            val pb = b.split('.').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val d = pa.getOrElse(i) { 0 } - pb.getOrElse(i) { 0 }
                if (d != 0) return d
            }
            return 0
        }
    }
}

/** The hash git (and GitHub's tree listing) uses for a file's content. */
internal fun gitBlobSha(bytes: ByteArray): String {
    val sha = MessageDigest.getInstance("SHA-1")
    sha.update("blob ${bytes.size}\u0000".toByteArray())
    return sha.digest(bytes).joinToString("") { "%02x".format(it) }
}
