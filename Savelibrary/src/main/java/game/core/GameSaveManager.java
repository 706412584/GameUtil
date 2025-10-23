package game.core;

import game.core.util.AESUtils;
import game.core.util.JsonImportUtil;
import org.json.JSONObject;

import java.io.File;
import java.util.List;
import java.util.Map;

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
	
	// 新功能模块
	private volatile JsonImportUtil jsonImportUtil;
	private volatile AsyncSaveManager asyncSaveManager;
	private volatile PerformanceMonitor performanceMonitor;
	
	// 性能优化选项
	private volatile boolean enableCompression = false; // 是否启用压缩
	private volatile int compressionThreshold = 10240; // 压缩阈值（10KB）
	private volatile boolean enablePerformanceMonitor = false; // 是否启用性能监控
	
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
		
		// 初始化新功能模块
		this.jsonImportUtil = new JsonImportUtil(this.logger);
		this.asyncSaveManager = new AsyncSaveManager(this, this.logger);
		this.performanceMonitor = new PerformanceMonitor(this.logger);
		
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
	* 设置是否启用压缩
	*/
	public synchronized void setCompressionEnabled(boolean enabled) {
		synchronized (lock) {
			this.enableCompression = enabled;
			logInfo(TAG, "数据压缩已" + (enabled ? "启用" : "禁用"));
		}
	}
	
	/**
	* 设置压缩阈值（字节）
	*/
	public synchronized void setCompressionThreshold(int threshold) {
		synchronized (lock) {
			this.compressionThreshold = threshold;
			logInfo(TAG, "压缩阈值设置为: " + threshold + " 字节");
		}
	}
	
	/**
	* 设置是否启用性能监控
	*/
	public synchronized void setPerformanceMonitorEnabled(boolean enabled) {
		synchronized (lock) {
			this.enablePerformanceMonitor = enabled;
			this.performanceMonitor.setEnabled(enabled);
			logInfo(TAG, "性能监控已" + (enabled ? "启用" : "禁用"));
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
		long startTime = performanceMonitor.startTimer();
		SaveData result = loadSave(saveId, "main", true);
		performanceMonitor.endTimer(PerformanceMonitor.Operation.READ, startTime, result != null);
		return result;
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
		long startTime = performanceMonitor.startTimer();
		boolean result = saveSave(saveData, "main", true);
		performanceMonitor.endTimer(PerformanceMonitor.Operation.WRITE, startTime, result);
		return result;
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
			
			// 如果不存在，创建新存档
			if (saveData == null) {
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
	
	// ==================== JSON导入功能 ====================
	
	/**
	* 从完整JSON导入存档（自动创建主表和分表）
	* @param saveId 存档ID
	* @param jsonStr JSON字符串
	* @param strategy 分表策略（null表示不分表，全部放入主表）
	* @return 导入结果
	*/
	public synchronized JsonImportUtil.ImportResult importFromJson(String saveId, String jsonStr, 
	                                                               JsonImportUtil.TableSplitStrategy strategy) {
		long startTime = performanceMonitor.startTimer();
		
		try {
			logInfo(TAG, "开始导入JSON存档: " + saveId);
			
			// 使用JsonImportUtil解析JSON
			JsonImportUtil.ImportResult result = jsonImportUtil.importJson(saveId, jsonStr, strategy);
			
			if (!result.success) {
				logError(TAG, "JSON导入失败");
				performanceMonitor.endTimer(PerformanceMonitor.Operation.IMPORT, startTime, false);
				return result;
			}
			
			// 保存主表数据
			if (result.mainTableData != null && !result.mainTableData.isEmpty()) {
				SaveData mainData = new SaveData(saveId, new JSONObject(result.mainTableData));
				boolean success = saveSave(mainData, "main", true);
				if (!success) {
					logError(TAG, "保存主表失败");
					result.success = false;
					result.errors.add("保存主表失败");
					performanceMonitor.endTimer(PerformanceMonitor.Operation.IMPORT, startTime, false);
					return result;
				}
			}
			
			// 保存分表数据
			for (Map.Entry<String, String> entry : result.subTableData.entrySet()) {
				String tableName = entry.getKey();
				String tableJsonStr = entry.getValue();
				
				// 创建分表（如果不存在）
				createTable(tableName);
				
				// 保存分表数据
				SaveData tableData = new SaveData(saveId + "_" + tableName, new JSONObject(tableJsonStr));
				boolean success = saveSave(tableData, tableName, true);
				if (!success) {
					logWarn(TAG, "保存分表失败: " + tableName);
					result.errors.add("保存分表失败: " + tableName);
				}
			}
			
			logInfo(TAG, "JSON导入完成: " + result.toString());
			performanceMonitor.endTimer(PerformanceMonitor.Operation.IMPORT, startTime, true);
			return result;
			
		} catch (Exception e) {
			logError(TAG, "导入JSON异常: " + e.getMessage(), e);
			performanceMonitor.endTimer(PerformanceMonitor.Operation.IMPORT, startTime, false);
			
			JsonImportUtil.ImportResult errorResult = new JsonImportUtil.ImportResult();
			errorResult.success = false;
			errorResult.errors.add("导入异常: " + e.getMessage());
			return errorResult;
		}
	}
	
	/**
	* 从完整JSON导入存档（不分表）
	*/
	public synchronized JsonImportUtil.ImportResult importFromJson(String saveId, String jsonStr) {
		return importFromJson(saveId, jsonStr, null);
	}
	
	/**
	* 使用前缀策略导入JSON
	* @param saveId 存档ID
	* @param jsonStr JSON字符串
	* @param prefixMapping 前缀映射（例如：{"inventory_" -> "inventory", "quest_" -> "quest"}）
	*/
	public synchronized JsonImportUtil.ImportResult importFromJsonWithPrefix(String saveId, String jsonStr,
	                                                                         Map<String, String> prefixMapping) {
		JsonImportUtil.PrefixBasedStrategy strategy = new JsonImportUtil.PrefixBasedStrategy(prefixMapping);
		return importFromJson(saveId, jsonStr, strategy);
	}
	
	/**
	* 使用大小策略导入JSON（大对象自动分表）
	* @param saveId 存档ID
	* @param jsonStr JSON字符串
	* @param sizeThreshold 大小阈值（字节），超过此大小的对象会被放入单独的分表
	*/
	public synchronized JsonImportUtil.ImportResult importFromJsonWithSize(String saveId, String jsonStr,
	                                                                       int sizeThreshold) {
		JsonImportUtil.SizeBasedStrategy strategy = new JsonImportUtil.SizeBasedStrategy(sizeThreshold);
		return importFromJson(saveId, jsonStr, strategy);
	}
	
	/**
	* 批量导入多个JSON存档
	*/
	public synchronized List<JsonImportUtil.ImportResult> batchImportFromJson(
	        Map<String, String> saveJsonMap, JsonImportUtil.TableSplitStrategy strategy) {
		logInfo(TAG, "开始批量导入JSON，数量: " + saveJsonMap.size());
		
		List<JsonImportUtil.ImportResult> results = new java.util.ArrayList<>();
		for (Map.Entry<String, String> entry : saveJsonMap.entrySet()) {
			JsonImportUtil.ImportResult result = importFromJson(entry.getKey(), entry.getValue(), strategy);
			results.add(result);
		}
		
		// 统计
		int successCount = 0;
		int failCount = 0;
		for (JsonImportUtil.ImportResult result : results) {
			if (result.success) {
				successCount++;
			} else {
				failCount++;
			}
		}
		
		logInfo(TAG, "批量导入完成 - 成功: " + successCount + ", 失败: " + failCount);
		return results;
	}
	
	// ==================== 异步保存功能 ====================
	
	/**
	* 异步保存存档
	*/
	public java.util.concurrent.Future<Boolean> saveAsync(SaveData saveData, AsyncSaveManager.SaveCallback callback) {
		return asyncSaveManager.saveAsync(saveData, callback);
	}
	
	/**
	* 异步保存存档（完整参数）
	*/
	public java.util.concurrent.Future<Boolean> saveAsync(SaveData saveData, String tableName, 
	                                                       boolean enableAntiCheat, AsyncSaveManager.SaveCallback callback) {
		return asyncSaveManager.saveAsync(saveData, tableName, enableAntiCheat, callback);
	}
	
	/**
	* 批量异步保存
	*/
	public AsyncSaveManager.BatchResult batchSaveAsync(List<SaveData> saveDataList) {
		return asyncSaveManager.batchSave(saveDataList);
	}
	
	/**
	* 批量异步保存（完整参数）
	*/
	public AsyncSaveManager.BatchResult batchSaveAsync(List<SaveData> saveDataList, String tableName, 
	                                                    boolean enableAntiCheat) {
		return asyncSaveManager.batchSave(saveDataList, tableName, enableAntiCheat);
	}
	
	/**
	* 批量异步读取
	*/
	public List<SaveData> batchLoadAsync(List<String> saveIds) {
		return asyncSaveManager.batchLoad(saveIds);
	}
	
	/**
	* 批量异步读取（完整参数）
	*/
	public List<SaveData> batchLoadAsync(List<String> saveIds, String tableName, boolean enableAntiCheat) {
		return asyncSaveManager.batchLoad(saveIds, tableName, enableAntiCheat);
	}
	
	/**
	* 等待所有异步任务完成
	*/
	public boolean waitForAsyncTasks(long timeoutMs) {
		return asyncSaveManager.waitForAll(timeoutMs);
	}
	
	// ==================== 性能监控功能 ====================
	
	/**
	* 获取性能监控器
	*/
	public PerformanceMonitor getPerformanceMonitor() {
		return performanceMonitor;
	}
	
	/**
	* 获取性能统计报告
	*/
	public String getPerformanceReport() {
		return performanceMonitor.getAllStats();
	}
	
	/**
	* 打印性能报告
	*/
	public void printPerformanceReport() {
		performanceMonitor.printReport();
	}
	
	/**
	* 获取缓存统计信息
	*/
	public String getCacheStats() {
		return tableManager.getCacheStats();
	}
	
	/**
	* 获取缓存命中率
	*/
	public float getCacheHitRate() {
		return tableManager.getCacheHitRate();
	}
	
	/**
	* 获取异步任务统计
	*/
	public String getAsyncStats() {
		return asyncSaveManager.getStats();
	}
	
	/**
	* 获取完整的系统统计信息
	*/
	public String getSystemStats() {
		StringBuilder sb = new StringBuilder();
		sb.append("=== 游戏存档系统统计 ===\n\n");
		sb.append(getSaveInfo()).append("\n");
		sb.append("--- 缓存统计 ---\n").append(getCacheStats()).append("\n");
		sb.append("--- 异步任务统计 ---\n").append(getAsyncStats()).append("\n");
		if (enablePerformanceMonitor) {
			sb.append("--- 性能统计 ---\n").append(performanceMonitor.getSummary()).append("\n");
		}
		return sb.toString();
	}
	
	/**
	* 关闭管理器（释放资源）
	*/
	public synchronized void shutdown() {
		logInfo(TAG, "关闭GameSaveManager...");
		
		// 等待异步任务完成
		asyncSaveManager.waitForAll(5000);
		
		// 关闭异步管理器
		asyncSaveManager.shutdown();
		
		// 清空缓存
		tableManager.clearCache();
		antiCheatManager.clearCache();
		
		logInfo(TAG, "GameSaveManager已关闭");
	}
}