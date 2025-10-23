package game.core.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LRU（Least Recently Used）缓存实现
 * 用于缓存存档数据，提升读取性能
 * 
 * 特性：
 * 1. 自动淘汰最少使用的数据
 * 2. 线程安全
 * 3. 支持自定义容量
 * 4. 支持缓存统计
 */
public class LruCache<K, V> {
    private final int maxSize;
    private final LinkedHashMap<K, V> cache;
    
    // 缓存统计
    private long hitCount = 0;
    private long missCount = 0;
    private long putCount = 0;
    private long evictionCount = 0;
    
    /**
     * 构造函数
     * @param maxSize 最大缓存条目数
     */
    public LruCache(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize <= 0");
        }
        this.maxSize = maxSize;
        
        // LinkedHashMap的accessOrder设为true，实现LRU
        this.cache = new LinkedHashMap<K, V>(0, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                boolean shouldRemove = size() > LruCache.this.maxSize;
                if (shouldRemove) {
                    evictionCount++;
                }
                return shouldRemove;
            }
        };
    }
    
    /**
     * 获取缓存值
     */
    public synchronized V get(K key) {
        V value = cache.get(key);
        if (value != null) {
            hitCount++;
        } else {
            missCount++;
        }
        return value;
    }
    
    /**
     * 放入缓存
     */
    public synchronized V put(K key, V value) {
        if (key == null || value == null) {
            throw new NullPointerException("key == null || value == null");
        }
        
        putCount++;
        return cache.put(key, value);
    }
    
    /**
     * 移除缓存项
     */
    public synchronized V remove(K key) {
        return cache.remove(key);
    }
    
    /**
     * 清空缓存
     */
    public synchronized void clear() {
        cache.clear();
        resetStats();
    }
    
    /**
     * 获取缓存大小
     */
    public synchronized int size() {
        return cache.size();
    }
    
    /**
     * 获取最大容量
     */
    public int maxSize() {
        return maxSize;
    }
    
    /**
     * 检查是否包含键
     */
    public synchronized boolean containsKey(K key) {
        return cache.containsKey(key);
    }
    
    /**
     * 获取命中率
     */
    public synchronized float getHitRate() {
        long total = hitCount + missCount;
        if (total == 0) {
            return 0.0f;
        }
        return (float) hitCount / total;
    }
    
    /**
     * 获取命中次数
     */
    public synchronized long getHitCount() {
        return hitCount;
    }
    
    /**
     * 获取未命中次数
     */
    public synchronized long getMissCount() {
        return missCount;
    }
    
    /**
     * 获取放入次数
     */
    public synchronized long getPutCount() {
        return putCount;
    }
    
    /**
     * 获取淘汰次数
     */
    public synchronized long getEvictionCount() {
        return evictionCount;
    }
    
    /**
     * 重置统计信息
     */
    public synchronized void resetStats() {
        hitCount = 0;
        missCount = 0;
        putCount = 0;
        evictionCount = 0;
    }
    
    /**
     * 获取缓存统计信息
     */
    public synchronized String getStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("LRU Cache Statistics:\n");
        sb.append("- Size: ").append(size()).append(" / ").append(maxSize).append("\n");
        sb.append("- Hit Rate: ").append(String.format("%.2f%%", getHitRate() * 100)).append("\n");
        sb.append("- Hit Count: ").append(hitCount).append("\n");
        sb.append("- Miss Count: ").append(missCount).append("\n");
        sb.append("- Put Count: ").append(putCount).append("\n");
        sb.append("- Eviction Count: ").append(evictionCount).append("\n");
        return sb.toString();
    }
    
    @Override
    public synchronized String toString() {
        return getStats();
    }
}


