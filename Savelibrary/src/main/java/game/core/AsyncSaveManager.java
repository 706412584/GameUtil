package game.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 异步存档管理器
 * 提供异步保存、批量操作等高性能功能
 * 
 * 特性：
 * 1. 异步保存存档，不阻塞主线程
 * 2. 批量操作支持
 * 3. 任务队列管理
 * 4. 保存完成回调
 * 5. 错误处理和重试机制
 */
public class AsyncSaveManager {
    private static final String TAG = "AsyncSaveManager";
    
    private final GameSaveManager saveManager;
    private final Logger logger;
    private final ExecutorService executorService;
    
    // 统计信息
    private final AtomicInteger pendingTasks = new AtomicInteger(0);
    private final AtomicInteger completedTasks = new AtomicInteger(0);
    private final AtomicInteger failedTasks = new AtomicInteger(0);
    
    /**
     * 保存结果回调接口
     */
    public interface SaveCallback {
        /**
         * 保存成功
         */
        void onSuccess(String saveId);
        
        /**
         * 保存失败
         */
        void onFailure(String saveId, Exception error);
    }
    
    /**
     * 批量操作结果
     */
    public static class BatchResult {
        public int total;
        public int success;
        public int failed;
        public long totalTime;
        public List<String> failedIds;
        
        public BatchResult() {
            this.failedIds = new ArrayList<>();
        }
        
        @Override
        public String toString() {
            return String.format("BatchResult{total=%d, success=%d, failed=%d, time=%dms}", 
                total, success, failed, totalTime);
        }
    }
    
    /**
     * 构造函数
     * @param saveManager 存档管理器
     * @param logger 日志接口
     * @param threadCount 线程池大小（0表示使用默认值）
     */
    public AsyncSaveManager(GameSaveManager saveManager, Logger logger, int threadCount) {
        this.saveManager = saveManager;
        this.logger = logger != null ? logger : new Logger.DefaultLogger();
        
        // 创建线程池
        int threads = threadCount > 0 ? threadCount : Runtime.getRuntime().availableProcessors();
        this.executorService = Executors.newFixedThreadPool(threads);
        
        logger.i(TAG, "AsyncSaveManager初始化完成，线程数: " + threads);
    }
    
    /**
     * 构造函数（使用默认线程数）
     */
    public AsyncSaveManager(GameSaveManager saveManager, Logger logger) {
        this(saveManager, logger, 0);
    }
    
    /**
     * 异步保存存档
     * @param saveData 存档数据
     * @param callback 保存回调（可为null）
     * @return Future对象，可用于取消任务或等待完成
     */
    public Future<Boolean> saveAsync(SaveData saveData, SaveCallback callback) {
        return saveAsync(saveData, "main", true, callback);
    }
    
    /**
     * 异步保存存档（完整参数）
     */
    public Future<Boolean> saveAsync(final SaveData saveData, final String tableName, 
                                     final boolean enableAntiCheat, final SaveCallback callback) {
        pendingTasks.incrementAndGet();
        
        return executorService.submit(() -> {
            try {
                logger.d(TAG, "开始异步保存: " + saveData.getSaveId() + " [表: " + tableName + "]");
                
                boolean success = saveManager.saveSave(saveData, tableName, enableAntiCheat);
                
                if (success) {
                    completedTasks.incrementAndGet();
                    if (callback != null) {
                        callback.onSuccess(saveData.getSaveId());
                    }
                    logger.d(TAG, "异步保存成功: " + saveData.getSaveId());
                } else {
                    failedTasks.incrementAndGet();
                    if (callback != null) {
                        callback.onFailure(saveData.getSaveId(), new Exception("保存失败"));
                    }
                    logger.e(TAG, "异步保存失败: " + saveData.getSaveId());
                }
                
                return success;
                
            } catch (Exception e) {
                failedTasks.incrementAndGet();
                if (callback != null) {
                    callback.onFailure(saveData.getSaveId(), e);
                }
                logger.e(TAG, "异步保存异常: " + e.getMessage());
                return false;
                
            } finally {
                pendingTasks.decrementAndGet();
            }
        });
    }
    
    /**
     * 批量保存多个存档
     * @param saveDataList 存档列表
     * @param tableName 表名
     * @param enableAntiCheat 是否启用反作弊
     * @return 批量操作结果
     */
    public BatchResult batchSave(List<SaveData> saveDataList, String tableName, boolean enableAntiCheat) {
        BatchResult result = new BatchResult();
        result.total = saveDataList.size();
        long startTime = System.currentTimeMillis();
        
        logger.i(TAG, "开始批量保存，数量: " + result.total);
        
        List<Future<Boolean>> futures = new ArrayList<>();
        
        // 提交所有任务
        for (SaveData saveData : saveDataList) {
            Future<Boolean> future = saveAsync(saveData, tableName, enableAntiCheat, null);
            futures.add(future);
        }
        
        // 等待所有任务完成
        for (int i = 0; i < futures.size(); i++) {
            try {
                boolean success = futures.get(i).get();
                if (success) {
                    result.success++;
                } else {
                    result.failed++;
                    result.failedIds.add(saveDataList.get(i).getSaveId());
                }
            } catch (Exception e) {
                result.failed++;
                result.failedIds.add(saveDataList.get(i).getSaveId());
                logger.e(TAG, "批量保存任务异常: " + e.getMessage());
            }
        }
        
        result.totalTime = System.currentTimeMillis() - startTime;
        logger.i(TAG, "批量保存完成: " + result.toString());
        
        return result;
    }
    
    /**
     * 批量保存（使用主表和默认设置）
     */
    public BatchResult batchSave(List<SaveData> saveDataList) {
        return batchSave(saveDataList, "main", true);
    }
    
    /**
     * 批量读取多个存档
     */
    public List<SaveData> batchLoad(List<String> saveIds, String tableName, boolean enableAntiCheat) {
        logger.i(TAG, "开始批量读取，数量: " + saveIds.size());
        
        List<SaveData> results = new ArrayList<>();
        List<Future<SaveData>> futures = new ArrayList<>();
        
        // 提交所有读取任务
        for (final String saveId : saveIds) {
            Future<SaveData> future = executorService.submit(() -> {
                return saveManager.loadSave(saveId, tableName, enableAntiCheat);
            });
            futures.add(future);
        }
        
        // 收集结果
        for (Future<SaveData> future : futures) {
            try {
                SaveData data = future.get();
                if (data != null) {
                    results.add(data);
                }
            } catch (Exception e) {
                logger.e(TAG, "批量读取任务异常: " + e.getMessage());
            }
        }
        
        logger.i(TAG, "批量读取完成，成功: " + results.size() + "/" + saveIds.size());
        
        return results;
    }
    
    /**
     * 批量读取（使用主表和默认设置）
     */
    public List<SaveData> batchLoad(List<String> saveIds) {
        return batchLoad(saveIds, "main", true);
    }
    
    /**
     * 等待所有任务完成
     * @param timeoutMs 超时时间（毫秒）
     * @return true表示所有任务完成，false表示超时
     */
    public boolean waitForAll(long timeoutMs) {
        long startTime = System.currentTimeMillis();
        
        while (pendingTasks.get() > 0) {
            if (System.currentTimeMillis() - startTime > timeoutMs) {
                logger.w(TAG, "等待任务完成超时");
                return false;
            }
            
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 获取待处理任务数
     */
    public int getPendingTaskCount() {
        return pendingTasks.get();
    }
    
    /**
     * 获取已完成任务数
     */
    public int getCompletedTaskCount() {
        return completedTasks.get();
    }
    
    /**
     * 获取失败任务数
     */
    public int getFailedTaskCount() {
        return failedTasks.get();
    }
    
    /**
     * 重置统计信息
     */
    public void resetStats() {
        completedTasks.set(0);
        failedTasks.set(0);
    }
    
    /**
     * 获取统计信息
     */
    public String getStats() {
        return String.format("AsyncSaveManager Stats - Pending: %d, Completed: %d, Failed: %d",
            pendingTasks.get(), completedTasks.get(), failedTasks.get());
    }
    
    /**
     * 关闭异步管理器
     */
    public void shutdown() {
        logger.i(TAG, "关闭AsyncSaveManager...");
        executorService.shutdown();
        
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.w(TAG, "强制关闭线程池");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.e(TAG, "关闭异常: " + e.getMessage());
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 检查是否已关闭
     */
    public boolean isShutdown() {
        return executorService.isShutdown();
    }
}

