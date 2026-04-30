package cp.player.provider

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object ModuleManager {
    private const val TAG = "ModuleManager"
    private val gson = Gson()
    private val providers = mutableMapOf<String, BackendProvider>()

    fun init(context: Context) {
        val modulesDir = File(context.filesDir, "modules")
        if (!modulesDir.exists()) modulesDir.mkdirs()
        
        modulesDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                loadModule(dir)
            }
        }
    }

    fun importModule(context: Context, zipFile: File): Boolean {
        val modulesDir = File(context.filesDir, "modules")
        if (!modulesDir.exists()) modulesDir.mkdirs()

        try {
            val tempDir = File(modulesDir, "temp_${System.currentTimeMillis()}")
            tempDir.mkdirs()

            ZipInputStream(zipFile.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val newFile = File(tempDir, entry.name)
                    val destDirPath = tempDir.canonicalPath
                    val destFilePath = newFile.canonicalPath
                    if (!destFilePath.startsWith(destDirPath + File.separator)) {
                        throw SecurityException("Entry is outside of the target dir: ${entry.name}")
                    }

                    if (entry.isDirectory) {
                        newFile.mkdirs()
                    } else {
                        newFile.parentFile?.mkdirs()
                        FileOutputStream(newFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    entry = zis.nextEntry
                }
            }

            val manifestFile = File(tempDir, "manifest.json")
            if (!manifestFile.exists()) {
                tempDir.deleteRecursively()
                Log.e(TAG, "No manifest.json found in module")
                return false
            }

            val manifestText = manifestFile.readText()
            val manifest = gson.fromJson(manifestText, ModuleManifest::class.java)
            val targetDir = File(modulesDir, manifest.id)
            if (targetDir.exists()) targetDir.deleteRecursively()
            tempDir.renameTo(targetDir)

            return loadModule(targetDir)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import module", e)
            return false
        }
    }

    private fun loadModule(dir: File): Boolean {
        val manifestFile = File(dir, "manifest.json")
        if (!manifestFile.exists()) return false

        try {
            val manifestText = manifestFile.readText()
            val manifest = gson.fromJson(manifestText, ModuleManifest::class.java)
            val entryPointFile = File(dir, manifest.entryPoint)

            val provider = when (manifest.type) {
                "jni" -> JniProvider(manifest.id, manifest.name, manifest.version, entryPointFile.absolutePath, manifest.apiMap)
                "binary" -> BinaryProvider(manifest.id, manifest.name, manifest.version, entryPointFile.absolutePath, manifest.apiMap)
                "http" -> HttpProvider(manifest.id, manifest.name, manifest.version, manifest.entryPoint, manifest.apiMap)
                else -> throw IllegalArgumentException("Unknown type: ${manifest.type}")
            }

            providers[manifest.id] = provider
            Log.i(TAG, "Loaded module: ${manifest.name} (${manifest.id})")
            
            if (ProviderManager.currentProvider == null) {
                ProviderManager.currentProvider = provider
            }
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load module from ${dir.name}", e)
            return false
        }
    }

    fun getAvailableProviders(): List<BackendProvider> = providers.values.toList()
    
    fun getProvider(id: String): BackendProvider? = providers[id]
}
