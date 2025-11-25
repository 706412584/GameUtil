package game.core;

import game.core.util.AESUtils;
import org.json.JSONObject;

import java.io.File;

/**
 * 游戏存档管理器 SDK版本（线程安全）
 *
 * 核心功能：
 * 1. 读取存档（带AES解密）
 * 2. 写入存档（带AES加密）
 * 3. 更新存档
 * 4. 设置存档地址
 * 5. 分表支持
 * 6. 反作弊功能（支持自定义反作弊类）
 * 7. 并发安全保护
 * 8. 可配置的日志开关
 */
public class GameSaveManager {
    private static final String TAG = "GameSaveManager";

    private volatile File saveDir;
    private volatile String encryptionKey = "GameSaveDefaultKey2025"; // 默认加密密钥

    private volatile SaveTableManager tableManager;
    private volatile AntiCheatManager antiCheatManager; // 改为volatile，支持动态更换
    private volatile Logger logger;

    private volatile String deviceId; // 改为volatile，支持重新初始化


    // 日志开关
    private volatile boolean logEnabled = true;

    // 同步锁对象
    private final Object lock = new Object();

    /**
     * 构造函数
     * @param saveDir 存档目录
     */
    public GameSaveManager(File saveDir) {
        this(saveDir, null, null);
    }

    /**
     * 构造函数
     * @param saveDir 存档目录
     * @param logger 日志接口（可为null，将使用默认日志）
     */
    public GameSaveManager(File saveDir, Logger logger) {
        this(saveDir, logger, null);
    }

    /**
     * 构造函数（支持传入自定义反作弊管理器）
     * @param saveDir 存档目录
     * @param logger 日志接口
     * @param antiCheatManager 反作弊管理器（可为null，将使用默认反作弊管理器）
     */
    public GameSaveManager(File saveDir, Logger logger, AntiCheatManager antiCheatManager) {
        this.saveDir = saveDir;
        this.logger = logger != null ? logger : new Logger.DefaultLogger();

        if (!saveDir.exists()) {
            saveDir.mkdirs();
        }

        // 初始化管理器
        this.tableManager = new SaveTableManager(saveDir, this.logger);
        this.antiCheatManager = antiCheatManager != null ? antiCheatManager : new AntiCheatManager(this.logger);

        // 获取设备ID
        File deviceIdFile = new File(saveDir.getParentFile(), ".device_id");
        this.deviceId = this.antiCheatManager.getDeviceId(deviceIdFile);

        logInfo(TAG, "GameSaveManager初始化完成");
        logInfo(TAG, "存档目录: " + saveDir.getAbsolutePath());
        logDebug(TAG, "设备ID: " + deviceId);
    }

    /**
     * 设置反作弊管理器（线程安全）- 方法
     */
    public synchronized void setAntiCheatManager(AntiCheatManager antiCheatManager) {
        synchronized (lock) {
            this.antiCheatManager = antiCheatManager != null ? antiCheatManager : new AntiCheatManager(this.logger);

            // 重新初始化设备ID
            File deviceIdFile = new File(saveDir.getParentFile(), ".device_id");
            this.deviceId = this.antiCheatManager.getDeviceId(deviceIdFile);

            logInfo(TAG, "反作弊管理器已更新");
            logDebug(TAG, "设备ID已重新初始化: " + deviceId);
        }
    }

    /**
     * 获取当前反作弊管理器 - 方法
     */
    public AntiCheatManager getAntiCheatManager() {
        return antiCheatManager;
    }

    /**
     * 设置日志开关（线程安全）
     */
    public synchronized void setLogEnabled(boolean enabled) {
        synchronized (lock) {
            this.logEnabled = enabled;
            if (enabled) {
                System.out.println(TAG + ": 日志输出已开启");
            }
        }
    }

    /**
     * 获取日志开关状态
     */
    public boolean isLogEnabled() {
        return logEnabled;
    }

    /**
     * 设置存档目录（线程安全）
     */
    public synchronized void setSaveDirectory(String path) {
        synchronized (lock) {
            File newDir = new File(path);
            if (!newDir.exists()) {
                newDir.mkdirs();
            }

            this.saveDir = newDir;
            this.tableManager = new SaveTableManager(newDir, this.logger);

            logInfo(TAG, "存档目录已更改: " + path);
        }
    }

    /**
     * 设置加密密钥（线程安全）
     */
    public synchronized void setEncryptionKey(String key) {
        synchronized (lock) {
            this.encryptionKey = key;
            logDebug(TAG, "加密密钥已更新");
        }
    }

    /**
     * 设置日志接口（线程安全）
     */
    public synchronized void setLogger(Logger logger) {
        synchronized (lock) {
            this.logger = logger != null ? logger : new Logger.DefaultLogger();
        }
    }

    // 条件日志方法，避免不必要的字符串拼接[1,2](@ref)
    private void logInfo(String tag, String message) {
        if (logEnabled) {
            logger.i(tag, message);
        }
    }

    private void logDebug(String tag, String message) {
        if (logEnabled) {
            logger.d(tag, message);
        }
    }

    private void logWarn(String tag, String message) {
        if (logEnabled) {
            logger.w(tag, message);
        }
    }

    private void logError(String tag, String message) {
        if (logEnabled) {
            logger.e(tag, message);
        }
    }

    private void logError(String tag, String message, Exception e) {
        if (logEnabled) {
            logger.e(tag, message);
            if (logEnabled) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 读取存档（从主表）（线程安全）
     */
    public synchronized SaveData loadSave(String saveId) {
        return loadSave(saveId, "main", true);
    }

    /**
     * 读取存档（线程安全）
     *
     * @param saveId 存档ID
     * @param tableName 表名（"main"表示主表）
     * @param enableAntiCheat 是否启用反作弊检测
     */
    public synchronized SaveData loadSave(String saveId, String tableName, boolean enableAntiCheat) {
        final String currentEncryptionKey = this.encryptionKey;
        final AntiCheatManager currentAntiCheatManager = this.antiCheatManager;

        try {
            logInfo(TAG, "开始读取存档: " + saveId + " [表: " + tableName + "]");

            // 读取加密的数据
            String encryptedData = tableManager.readTable(tableName);

            if (encryptedData == null || encryptedData.isEmpty()) {
                logWarn(TAG, "存档不存在或为空");
                return null;
            }

            logDebug(TAG, "加密数据长度: " + encryptedData.length());

            // 解密数据
            String jsonData = AESUtils.decrypt(encryptedData, currentEncryptionKey);

            if (jsonData == null || jsonData.isEmpty()) {
                logError(TAG, "解密失败！");
                return null;
            }

            logDebug(TAG, "解密成功，数据长度: " + jsonData.length());

            // 验证完整性
            if (enableAntiCheat) {
                if (!currentAntiCheatManager.verifySaveData(jsonData)) {
                    logError(TAG, "存档完整性校验失败！");
                    return null;
                }
                logDebug(TAG, "完整性校验通过");
            }

            // 解析JSON
            JSONObject json = new JSONObject(jsonData);
            SaveData saveData = new SaveData(saveId, json);

            // 检测作弊
            if (enableAntiCheat) {
                if (currentAntiCheatManager.detectCopyCheat(saveData, deviceId)) {
                    logWarn(TAG, "检测到存档复制作弊！");
                }
            }

            logInfo(TAG, "存档读取成功");
            return saveData;

        } catch (Exception e) {
            logError(TAG, "读取存档失败: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * 保存存档（到主表）（线程安全）
     */
    public synchronized boolean saveSave(SaveData saveData) {
        return saveSave(saveData, "main", true);
    }

    /**
     * 保存存档（线程安全）
     *
     * @param saveData 存档数据
     * @param tableName 表名（"main"表示主表）
     * @param enableAntiCheat 是否启用反作弊
     */
    public synchronized boolean saveSave(SaveData saveData, String tableName, boolean enableAntiCheat) {
        final String currentEncryptionKey = this.encryptionKey;
        final AntiCheatManager currentAntiCheatManager = this.antiCheatManager;

        try {
            logInfo(TAG, "开始保存存档: " + saveData.getSaveId() + " [表: " + tableName + "]");

            // 更新反作弊元数据
            if (enableAntiCheat) {
                currentAntiCheatManager.updateSaveMetadata(saveData, deviceId);
            }

            // 转换为JSON
            String jsonData = saveData.toJsonString();

            logDebug(TAG, "JSON数据长度: " + jsonData.length());

            // 添加校验和
            if (enableAntiCheat) {
                jsonData = currentAntiCheatManager.addChecksumToSaveData(jsonData);
                logDebug(TAG, "已添加完整性校验和");
            }

            // 加密数据
            String encryptedData = AESUtils.encrypt(jsonData, currentEncryptionKey);

            if (encryptedData == null || encryptedData.isEmpty()) {
                logError(TAG, "加密失败！");
                return false;
            }

            logDebug(TAG, "加密成功，数据长度: " + encryptedData.length());

            // 写入文件
            boolean success = tableManager.writeTable(tableName, encryptedData);

            if (success) {
                logInfo(TAG, "存档保存成功");
            } else {
                logError(TAG, "存档保存失败");
            }

            return success;

        } catch (Exception e) {
            logError(TAG, "保存存档失败: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 更新存档（读取-修改-保存）（线程安全）
     */
    public synchronized boolean updateSave(String saveId, UpdateCallback callback) {
        return updateSave(saveId, "main", callback, true);
    }

    /**
     * 更新存档回调接口
     */
    public interface UpdateCallback {
        void onUpdate(SaveData saveData);
    }

    /**
     * 更新存档（线程安全）
     */
    public synchronized boolean updateSave(String saveId, String tableName, UpdateCallback callback, boolean enableAntiCheat) {
        try {
            logInfo(TAG, "开始更新存档: " + saveId);

            // 读取现有存档
            SaveData saveData = loadSave(saveId, tableName, enableAntiCheat);

            // 如果读取失败，需要区分是"不存在"还是"读取失败"
            if (saveData == null) {
                // 检查文件是否真的不存在
                File tableFile = tableName.equals("main") ?
                        tableManager.getMainTableFile() :
                        tableManager.getTableFile(tableName);

                if (tableFile.exists()) {
                    // 文件存在但读取失败，不应该覆盖！
                    logError(TAG, "存档文件存在但读取失败，拒绝更新以防止数据丢失！");
                    return false;
                }

                // 文件真的不存在，可以创建新存档
                logInfo(TAG, "存档不存在，创建新存档");
                saveData = new SaveData(saveId);
            }

            // 执行更新回调
            callback.onUpdate(saveData);

            // 保存
            return saveSave(saveData, tableName, enableAntiCheat);

        } catch (Exception e) {
            logError(TAG, "更新存档失败: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 删除存档
     */
    public boolean deleteSave(String tableName) {
        if (tableName.equals("main")) {
            File mainFile = tableManager.getMainTableFile();
            if (mainFile.exists()) {
                boolean deleted = mainFile.delete();
                logInfo(TAG, "删除主表存档: " + deleted);
                return deleted;
            }
            return false;
        } else {
            boolean deleted = tableManager.deleteTable(tableName);
            logInfo(TAG, "删除分表存档: " + tableName + " -> " + deleted);
            return deleted;
        }
    }

    /**
     * 创建分表
     */
    public boolean createTable(String tableName) {
        boolean created = tableManager.createTable(tableName);
        if (created) {
            logInfo(TAG, "创建分表: " + tableName);
        }
        return created;
    }

    /**
     * 获取所有分表
     */
    public java.util.List<String> getAllTables() {
        return tableManager.getAllTableNames();
    }

    /**
     * 备份存档
     */
    public boolean backupSave(String tableName) {
        boolean backed = tableManager.backupTable(tableName);
        if (backed) {
            logInfo(TAG, "备份存档: " + tableName);
        }
        return backed;
    }

    /**
     * 获取存档目录
     */
    public String getSaveDirectory() {
        return saveDir.getAbsolutePath();
    }

    /**
     * 获取设备ID
     */
    public String getDeviceId() {
        return deviceId;
    }

    /**
     * 清空所有存档
     */
    public void clearAllSaves() {
        tableManager.clearAllTables();
        antiCheatManager.clearCache();
        logInfo(TAG, "清空所有存档");
    }

    /**
     * 获取存档信息
     */
    public String getSaveInfo() {
        StringBuilder info = new StringBuilder();
        info.append("存档目录: ").append(saveDir.getAbsolutePath()).append("\n");
        info.append("主表大小: ").append(formatSize(tableManager.getTableSize("main"))).append("\n");
        info.append("分表数量: ").append(tableManager.getAllTableNames().size()).append("\n");
        info.append("总大小: ").append(formatSize(tableManager.getTotalSize())).append("\n");
        info.append("设备ID: ").append(deviceId).append("\n");
        return info.toString();
    }

    /**
     * 格式化文件大小
     */
    private String formatSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else {
            return String.format("%.2f MB", size / (1024.0 * 1024.0));
        }
    }

    /**
     * 读取主存档的JSON数据（解密后的原始JSON）
     *
     * @param saveId 存档ID
     * @return JSON字符串，失败返回null
     */
    public synchronized String loadSaveAsJson(String saveId) {
        return loadSaveAsJson(saveId, "main", false);
    }

    /**
     * 读取存档的JSON数据（解密后的原始JSON）
     *
     * @param saveId 存档ID
     * @param tableName 表名（"main"表示主表）
     * @param skipVerify 是否跳过反作弊验证（true=只解密不验证，false=完整验证）
     * @return JSON字符串，失败返回null
     */
    public synchronized String loadSaveAsJson(String saveId, String tableName, boolean skipVerify) {
        final String currentEncryptionKey = this.encryptionKey;
        final AntiCheatManager currentAntiCheatManager = this.antiCheatManager;

        try {
            logInfo(TAG, "读取存档JSON: " + saveId + " [表: " + tableName + "]");

            // 读取加密的数据
            String encryptedData = tableManager.readTable(tableName);

            if (encryptedData == null || encryptedData.isEmpty()) {
                logWarn(TAG, "存档不存在或为空");
                return null;
            }

            // 解密数据
            String jsonData = AESUtils.decrypt(encryptedData, currentEncryptionKey);

            if (jsonData == null || jsonData.isEmpty()) {
                logError(TAG, "解密失败！");
                return null;
            }

            // 可选：验证完整性
            if (!skipVerify && !currentAntiCheatManager.verifySaveData(jsonData)) {
                logError(TAG, "存档完整性校验失败！");
                return null;
            }

            logInfo(TAG, "读取JSON成功，长度: " + jsonData.length());
            return jsonData;

        } catch (Exception e) {
            logError(TAG, "读取存档JSON失败: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * 读取所有存档（主表+所有分表）并合并为一个JSON对象
     *
     * @return JSON字符串，包含所有表的数据，格式：{"main": {...}, "table_name1": {...}, ...}
     */
    public synchronized String loadAllSavesAsJson() {
        final AntiCheatManager currentAntiCheatManager = this.antiCheatManager;

        try {
            logInfo(TAG, "读取所有存档JSON");

            JSONObject allData = new JSONObject();

            // 读取主表
            String mainJson = loadSaveAsJson("main_data", "main", true);
            if (mainJson != null && !mainJson.isEmpty()) {
                try {
                    allData.put("main", new JSONObject(mainJson));
                } catch (Exception e) {
                    logWarn(TAG, "主表数据解析失败: " + e.getMessage());
                }
            }

            // 读取所有分表
            java.util.List<String> tables = tableManager.getAllTableNames();
            for (String tableName : tables) {
                String tableJson = loadSaveAsJson(tableName + "_data", tableName, true);
                if (tableJson != null && !tableJson.isEmpty()) {
                    try {
                        allData.put(tableName, new JSONObject(tableJson));
                    } catch (Exception e) {
                        logWarn(TAG, "分表 " + tableName + " 数据解析失败: " + e.getMessage());
                    }
                }
            }

            String result = allData.toString(2);
            logInfo(TAG, "读取所有存档成功，包含 " + allData.length() + " 个表");
            return result;

        } catch (Exception e) {
            logError(TAG, "读取所有存档JSON失败: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * 读取指定分表的JSON数据
     *
     * @param tableName 分表名称
     * @return JSON字符串，失败返回null
     */
    public synchronized String loadTableAsJson(String tableName) {
        return loadSaveAsJson(tableName + "_data", tableName, true);
    }

    /**
     * 批量读取多个分表的JSON数据
     *
     * @param tableNames 分表名称列表
     * @return JSON字符串，包含所有指定表的数据
     */
    public synchronized String loadTablesAsJson(java.util.List<String> tableNames) {
        final AntiCheatManager currentAntiCheatManager = this.antiCheatManager;

        try {
            logInfo(TAG, "批量读取存档JSON，表数量: " + tableNames.size());

            JSONObject allData = new JSONObject();

            for (String tableName : tableNames) {
                String tableJson = loadTableAsJson(tableName);
                if (tableJson != null && !tableJson.isEmpty()) {
                    try {
                        allData.put(tableName, new JSONObject(tableJson));
                    } catch (Exception e) {
                        logWarn(TAG, "分表 " + tableName + " 数据解析失败: " + e.getMessage());
                    }
                }
            }

            String result = allData.toString(2);
            logInfo(TAG, "批量读取成功，包含 " + allData.length() + " 个表");
            return result;

        } catch (Exception e) {
            logError(TAG, "批量读取存档JSON失败: " + e.getMessage(), e);
            return null;
        }
    }
}