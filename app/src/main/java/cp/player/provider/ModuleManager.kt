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

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        val modulesDir = File(context.filesDir, "modules")
        if (!modulesDir.exists()) modulesDir.mkdirs()

        modulesDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                loadModule(dir)
            }
        }

        // 尝试恢复上次用户选择的 Provider（优先于自动选择的第一个）
        if (ProviderManager.currentProvider != null) {
            ProviderManager.restoreLastProvider(context)
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
                "jni" -> JniProvider(manifest.id, manifest.name, manifest.version, entryPointFile.absolutePath, manifest.apiMap, manifest.updateUrl)
                "binary" -> BinaryProvider(manifest.id, manifest.name, manifest.version, entryPointFile.absolutePath, manifest.apiMap, manifest.updateUrl)
                "http" -> HttpProvider(manifest.id, manifest.name, manifest.version, manifest.entryPoint, manifest.apiMap, manifest.updateUrl)
                else -> throw IllegalArgumentException("Unknown type: ${manifest.type}")
            }

            providers[manifest.id] = provider
            Log.i(TAG, "Loaded module: ${manifest.name} (${manifest.id})")

            // 如果没有活跃 Provider，临时选择第一个加载的模块（不保存偏好，由 restoreLastProvider 决定最终选择）
            if (ProviderManager.currentProvider == null) {
                ProviderManager.switchProvider(provider, appContext, save = false)
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load module from ${dir.name}", e)
            return false
        }
    }

    fun getAvailableProviders(): List<BackendProvider> = providers.values.toList()

    fun getProvider(id: String): BackendProvider? = providers[id]

    /**
     * 获取模块目录路径。
     */
    fun getModuleDir(id: String): File? {
        val dir = File(appContext?.filesDir ?: return null, "modules/$id")
        return if (dir.exists()) dir else null
    }

    /**
     * 删除指定模块。
     * 调用方需先停止该 Provider 的服务。
     *
     * @return true 如果删除成功
     */
    fun deleteModule(id: String): Boolean {
        val dir = getModuleDir(id) ?: return false
        return try {
            dir.deleteRecursively()
            providers.remove(id)
            Log.i(TAG, "Deleted module: $id")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete module: $id", e)
            false
        }
    }

    /**
     * 更新模块，保留用户数据。
     *
     * 更新流程：
     * 1. 解压新 zip 到临时目录
     * 2. 验证 manifest.json 存在且 id 匹配
     * 3. 将旧模块中的 user_data/ 目录复制到临时目录
     * 4. 删除旧模块目录
     * 5. 将临时目录重命名为模块目录
     * 6. 重新加载模块
     *
     * @return true 如果更新成功
     */
    fun updateModule(context: Context, id: String, zipFile: File): Boolean {
        val modulesDir = File(context.filesDir, "modules")
        try {
            val tempDir = File(modulesDir, "temp_update_${System.currentTimeMillis()}")
            tempDir.mkdirs()

            // 解压新 zip
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
                Log.e(TAG, "No manifest.json found in update zip")
                return false
            }

            val manifest = gson.fromJson(manifestFile.readText(), ModuleManifest::class.java)
            if (manifest.id != id) {
                tempDir.deleteRecursively()
                Log.e(TAG, "Update zip id (${manifest.id}) does not match target ($id)")
                return false
            }

            // 保留旧模块的 user_data 目录
            val oldDir = File(modulesDir, id)
            val userDataDir = File(oldDir, "user_data")
            if (userDataDir.exists()) {
                val targetUserData = File(tempDir, "user_data")
                userDataDir.copyRecursively(targetUserData, overwrite = true)
                Log.i(TAG, "Preserved user_data for module: $id")
            }

            // 停止旧 Provider 并移除
            providers[id]?.stopServer()
            providers.remove(id)

            // 替换模块目录
            if (oldDir.exists()) oldDir.deleteRecursively()
            tempDir.renameTo(oldDir)

            return loadModule(oldDir)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update module: $id", e)
            return false
        }
    }
}
