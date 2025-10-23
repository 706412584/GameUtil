package game.core;

import game.core.util.LruCache;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 分表管理器（线程安全）
 * 类似数据库分表，将存档数据分散到多个文件中以提高性能
 * 
 * 性能优化：
 * 1. 使用LRU缓存提升读取性能
 * 2. 读写锁支持并发读取
 * 3. 支持缓存统计
 */
public class SaveTableManager {
    private static final String TAG = "SaveTableManager";
    
    private final File saveDir;
    private final String mainTableName = "main.save";
    private final Logger logger;
    
    // 分表缓存（线程安全） - 已废弃，使用LRU缓存替代
    @Deprecated
    private final Map<String, String> tableCache = new ConcurrentHashMap<>();
    
    // LRU缓存（提升性能）
    private final LruCache<String, String> lruCache;
    private final boolean useLruCache;
    
    // 读写锁，允许多个读操作，写操作独占
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    
    public SaveTableManager(File saveDir, Logger logger) {
        this(saveDir, logger, 50); // 默认缓存50个表
    }
    
    /**
     * 构造函数（支持自定义缓存大小）
     * @param saveDir 存档目录
     * @param logger 日志接口
     * @param cacheSize 缓存大小（0表示禁用LRU缓存）
     */
    public SaveTableManager(File saveDir, Logger logger, int cacheSize) {
        this.saveDir = saveDir;
        this.logger = logger != null ? logger : new Logger.DefaultLogger();
        this.useLruCache = cacheSize > 0;
        this.lruCache = useLruCache ? new LruCache<String, String>(cacheSize) : null;
        
        if (!saveDir.exists()) {
            saveDir.mkdirs();
        }
        
        if (useLruCache) {
            logger.i(TAG, "LRU缓存已启用，大小: " + cacheSize);
        }
    }
    
    /**
     * 获取主表文件
     */
    public File getMainTableFile() {
        return new File(saveDir, mainTableName);
    }
    
    /**
     * 获取分表文件
     */
    public File getTableFile(String tableName) {
        String fileName = "table_" + tableName + ".save";
        return new File(saveDir, fileName);
    }
    
    /**
     * 创建新分表（线程安全）
     */
    public synchronized boolean createTable(String tableName) {
        rwLock.writeLock().lock();
        try {
            File tableFile = getTableFile(tableName);
            if (!tableFile.exists()) {
                tableFile.createNewFile();
                logger.i(TAG, "创建分表: " + tableName);
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.e(TAG, "创建分表失败: " + tableName + " - " + e.getMessage());
            return false;
        } finally {
            rwLock.writeLock().unlock();
        }
    }
    
    /**
     * 删除分表（线程安全）
     */
    public synchronized boolean deleteTable(String tableName) {
        rwLock.writeLock().lock();
        try {
            File tableFile = getTableFile(tableName);
            if (tableFile.exists()) {
                boolean deleted = tableFile.delete();
                if (deleted) {
                    // 从缓存中移除
                    if (useLruCache) {
                        lruCache.remove(tableName);
                    } else {
                        tableCache.remove(tableName);
                    }
                    logger.i(TAG, "删除分表: " + tableName);
                }
                return deleted;
            }
            return false;
        } finally {
            rwLock.writeLock().unlock();
        }
    }
    
    /**
     * 获取所有分表名称
     */
    public List<String> getAllTableNames() {
        List<String> tableNames = new ArrayList<>();
        
        File[] files = saveDir.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (name.startsWith("table_") && name.endsWith(".save")) {
                    String tableName = name.substring(6, name.length() - 5);
                    tableNames.add(tableName);
                }
            }
        }
        
        return tableNames;
    }
    
    /**
     * 读取表数据（线程安全，使用读锁和LRU缓存）
     */
    public String readTable(String tableName) {
        // 先检查缓存
        if (useLruCache) {
            String cachedData = lruCache.get(tableName);
            if (cachedData != null) {
                return cachedData;
            }
        } else {
            String cachedData = tableCache.get(tableName);
            if (cachedData != null) {
                return cachedData;
            }
        }
        
        rwLock.readLock().lock();
        try {
            File tableFile = tableName.equals("main") ? getMainTableFile() : getTableFile(tableName);
            
            if (!tableFile.exists()) {
                return null;
            }
            
            try {
                StringBuilder content = new StringBuilder();
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.FileReader(tableFile));
                
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line);
                }
                reader.close();
                
                String data = content.toString();
                
                // 放入缓存
                if (useLruCache) {
                    lruCache.put(tableName, data);
                } else {
                    tableCache.put(tableName, data);
                }
                
                return data;
            } catch (Exception e) {
                logger.e(TAG, "读取表数据失败: " + tableName + " - " + e.getMessage());
                return null;
            }
        } finally {
            rwLock.readLock().unlock();
        }
    }
    
    /**
     * 写入表数据（线程安全，使用写锁，自动更新缓存）
     */
    public boolean writeTable(String tableName, String data) {
        rwLock.writeLock().lock();
        try {
            File tableFile = tableName.equals("main") ? getMainTableFile() : getTableFile(tableName);
            
            try {
                if (!tableFile.getParentFile().exists()) {
                    tableFile.getParentFile().mkdirs();
                }
                
                java.io.FileWriter writer = new java.io.FileWriter(tableFile);
                writer.write(data);
                writer.close();
                
                // 更新缓存
                if (useLruCache) {
                    lruCache.put(tableName, data);
                } else {
                    tableCache.put(tableName, data);
                }
                
                return true;
            } catch (Exception e) {
                logger.e(TAG, "写入表数据失败: " + tableName + " - " + e.getMessage());
                return false;
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }
    
    /**
     * 清空所有表（线程安全）
     */
    public synchronized void clearAllTables() {
        rwLock.writeLock().lock();
        try {
            File[] files = saveDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().endsWith(".save")) {
                        file.delete();
                    }
                }
            }
            
            // 清空缓存
            if (useLruCache) {
                lruCache.clear();
            } else {
                tableCache.clear();
            }
            
            logger.i(TAG, "清空所有表");
        } finally {
            rwLock.writeLock().unlock();
        }
    }
    
    /**
     * 获取表的大小（字节）
     */
    public long getTableSize(String tableName) {
        File tableFile = tableName.equals("main") ? getMainTableFile() : getTableFile(tableName);
        return tableFile.exists() ? tableFile.length() : 0;
    }
    
    /**
     * 获取所有表的总大小
     */
    public long getTotalSize() {
        long total = 0;
        File[] files = saveDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(".save")) {
                    total += file.length();
                }
            }
        }
        return total;
    }
    
    /**
     * 备份表
     */
    public boolean backupTable(String tableName) {
        File tableFile = tableName.equals("main") ? getMainTableFile() : getTableFile(tableName);
        
        if (!tableFile.exists()) {
            return false;
        }
        
        try {
            String backupName = tableName + "_backup_" + System.currentTimeMillis() + ".save";
            File backupFile = new File(saveDir, backupName);
            
            copyFile(tableFile, backupFile);
            
            logger.i(TAG, "备份表: " + tableName + " -> " + backupName);
            return true;
        } catch (Exception e) {
            logger.e(TAG, "备份表失败: " + tableName + " - " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 复制文件
     */
    private void copyFile(File source, File dest) throws Exception {
        java.io.FileInputStream fis = new java.io.FileInputStream(source);
        java.io.FileOutputStream fos = new java.io.FileOutputStream(dest);
        
        byte[] buffer = new byte[1024];
        int length;
        while ((length = fis.read(buffer)) > 0) {
            fos.write(buffer, 0, length);
        }
        
        fis.close();
        fos.close();
    }
    
    /**
     * 获取缓存的表数据
     */
    public String getCachedTable(String tableName) {
        if (useLruCache) {
            return lruCache.get(tableName);
        } else {
            return tableCache.get(tableName);
        }
    }
    
    /**
     * 清除缓存
     */
    public void clearCache() {
        if (useLruCache) {
            lruCache.clear();
        } else {
            tableCache.clear();
        }
    }
    
    /**
     * 获取缓存统计信息
     */
    public String getCacheStats() {
        if (useLruCache) {
            return lruCache.getStats();
        } else {
            return "缓存大小: " + tableCache.size() + " (使用ConcurrentHashMap)";
        }
    }
    
    /**
     * 获取缓存命中率
     */
    public float getCacheHitRate() {
        if (useLruCache) {
            return lruCache.getHitRate();
        } else {
            return 0.0f;
        }
    }
}

