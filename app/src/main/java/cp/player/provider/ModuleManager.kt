package cp.player.provider

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object ModuleManager {
    private const val TAG = "ModuleManager"
    private val gson = Gson()
    private val providers = mutableMapOf<String, BackendProvider>()

    private val _providersFlow = MutableStateFlow<List<BackendProvider>>(emptyList())
    /** 响应式的可用 Provider 列表流，供 UI 监听刷新 */
    val providersFlow: StateFlow<List<BackendProvider>> = _providersFlow.asStateFlow()

    private fun updateProvidersFlow() {
        _providersFlow.value = providers.values.toList()
    }

    /** 最近一次导入/加载失败的错误信息（供 UI 展示） */
    var lastLoadError: String? = null
        private set

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

        updateProvidersFlow()

        // 尝试恢复上次用户选择的 Provider
        val restored = ProviderManager.restoreLastProvider(context)
        // 如果没有成功恢复（例如第一次启动或上次的模块被删了），且当前有模块可用，则自动切换到第一个
        if (!restored && providers.isNotEmpty() && ProviderManager.currentProvider == null) {
            ProviderManager.switchProvider(providers.values.first(), appContext, save = false)
        }
    }

    fun importModule(context: Context, zipFile: File): Boolean {
        val modulesDir = File(context.filesDir, "modules")
        if (!modulesDir.exists()) modulesDir.mkdirs()
        lastLoadError = null

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
                lastLoadError = "模块包中缺少 manifest.json"
                Log.e(TAG, "No manifest.json found in module")
                return false
            }

            val manifestText = manifestFile.readText()
            val manifest = gson.fromJson(manifestText, ModuleManifest::class.java)
            val targetDir = File(modulesDir, manifest.id)
            if (targetDir.exists()) targetDir.deleteRecursively()
            tempDir.renameTo(targetDir)

            val success = loadModule(targetDir)
            if (!success) {
                // 加载失败时清理已移动的目录，避免残留损坏模块
                targetDir.deleteRecursively()
            } else {
                updateProvidersFlow()
            }
            return success
        } catch (e: SecurityException) {
            lastLoadError = "ZIP 路径不安全: ${e.message}"
            Log.e(TAG, "Failed to import module: ZIP path traversal", e)
            return false
        } catch (e: Exception) {
            lastLoadError = "导入失败: ${e.message}"
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

            // 解析入口文件路径：优先 per-ABI 目录，回退到根目录（向后兼容）
            val entryPointFile = resolveEntryPoint(dir, manifest)

            // 对 JNI/Binary 类型，预先检查入口文件是否存在
            if ((manifest.type == "jni" || manifest.type == "binary") && !entryPointFile.exists()) {
                lastLoadError = "${manifest.type.uppercase()} 入口文件不存在: ${manifest.entryPoint}"
                Log.e(TAG, "模块入口文件不存在: ${entryPointFile.absolutePath} (模块: ${manifest.id})")
                return false
            }

            val provider = when (manifest.type) {
                "jni" -> JniProvider(manifest.id, manifest.name, manifest.version, entryPointFile.absolutePath, manifest.apiMap, manifest.updateUrl)
                "binary" -> BinaryProvider(manifest.id, manifest.name, manifest.version, entryPointFile.absolutePath, manifest.apiMap, manifest.updateUrl)
                "http" -> HttpProvider(manifest.id, manifest.name, manifest.version, manifest.entryPoint, manifest.apiMap, manifest.updateUrl)
                else -> throw IllegalArgumentException("Unknown type: ${manifest.type}")
            }

            // 检查 Provider 是否就绪（JNI/Binary 模块可能加载失败）
            if (!provider.isReady()) {
                val error = when (provider) {
                    is JniProvider -> provider.getLoadError()
                    is BinaryProvider -> provider.getLoadError()
                    else -> "Provider not ready"
                } ?: "Provider not ready"
                lastLoadError = error
                Log.e(TAG, "模块加载失败，Provider 未就绪: ${manifest.id} ($error)")
                return false
            }

            providers[manifest.id] = provider
            Log.i(TAG, "Loaded module: ${manifest.name} (${manifest.id})")

            return true
        } catch (e: Exception) {
            lastLoadError = "加载失败: ${e.message}"
            Log.e(TAG, "Failed to load module from ${dir.name}", e)
            return false
        }
    }

    /**
     * 解析模块入口文件路径。
     *
     * 支持两种 zip 结构：
     * 1. **多 ABI 格式**：`lib/{abi}/{entryPoint}`（如 `lib/arm64-v8a/libfoo.so`）
     *    按设备 `Build.SUPPORTED_ABIS` 顺序查找，使用第一个匹配的。
     * 2. **单 ABI 格式**：`{entryPoint}`（如 `libfoo.so`，向后兼容）
     *
     * 优先使用多 ABI 格式，找不到时回退到根目录。
     */
    private fun resolveEntryPoint(dir: File, manifest: ModuleManifest): File {
        // 多 ABI 格式：按设备支持的 ABI 顺序查找
        for (abi in Build.SUPPORTED_ABIS) {
            val abiFile = File(dir, "lib/$abi/${manifest.entryPoint}")
            if (abiFile.exists()) {
                Log.i(TAG, "使用 ABI 专属入口: lib/$abi/${manifest.entryPoint}")
                return abiFile
            }
        }
        // 回退：根目录（单 ABI 格式，向后兼容）
        return File(dir, manifest.entryPoint)
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
            updateProvidersFlow()
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
        lastLoadError = null
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
                lastLoadError = "更新包中缺少 manifest.json"
                Log.e(TAG, "No manifest.json found in update zip")
                return false
            }

            val manifest = gson.fromJson(manifestFile.readText(), ModuleManifest::class.java)
            if (manifest.id != id) {
                tempDir.deleteRecursively()
                lastLoadError = "模块 ID 不匹配: 包含 ${manifest.id}，目标 $id"
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

            val loaded = loadModule(oldDir)
            if (loaded) {
                updateProvidersFlow()
            }
            return loaded
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update module: $id", e)
            return false
        }
    }
}
