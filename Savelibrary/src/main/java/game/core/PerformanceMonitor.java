package game.core;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 性能监控器
 * 用于监控存档系统的性能指标
 * 
 * 监控指标：
 * 1. 读取/写入操作次数和耗时
 * 2. 平均响应时间
 * 3. 最大/最小响应时间
 * 4. 吞吐量统计
 * 5. 错误率
 */
public class PerformanceMonitor {
    private static final String TAG = "PerformanceMonitor";
    
    // 操作类型
    public enum Operation {
        READ,
        WRITE,
        UPDATE,
        DELETE,
        IMPORT,
        EXPORT
    }
    
    // 性能指标
    private static class Metric {
        AtomicLong totalTime = new AtomicLong(0);
        AtomicInteger count = new AtomicInteger(0);
        AtomicLong maxTime = new AtomicLong(0);
        AtomicLong minTime = new AtomicLong(Long.MAX_VALUE);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        void record(long timeMs, boolean success) {
            totalTime.addAndGet(timeMs);
            count.incrementAndGet();
            
            if (!success) {
                errorCount.incrementAndGet();
            }
            
            // 更新最大值
            long currentMax = maxTime.get();
            while (timeMs > currentMax) {
                if (maxTime.compareAndSet(currentMax, timeMs)) {
                    break;
                }
                currentMax = maxTime.get();
            }
            
            // 更新最小值
            long currentMin = minTime.get();
            while (timeMs < currentMin) {
                if (minTime.compareAndSet(currentMin, timeMs)) {
                    break;
                }
                currentMin = minTime.get();
            }
        }
        
        double getAverage() {
            int c = count.get();
            if (c == 0) {
                return 0.0;
            }
            return (double) totalTime.get() / c;
        }
        
        double getErrorRate() {
            int c = count.get();
            if (c == 0) {
                return 0.0;
            }
            return (double) errorCount.get() / c * 100;
        }
    }
    
    private final ConcurrentHashMap<Operation, Metric> metrics;
    private final Logger logger;
    private final long startTime;
    private volatile boolean enabled = true;
    
    public PerformanceMonitor(Logger logger) {
        this.logger = logger != null ? logger : new Logger.DefaultLogger();
        this.metrics = new ConcurrentHashMap<>();
        this.startTime = System.currentTimeMillis();
        
        // 初始化所有操作类型的指标
        for (Operation op : Operation.values()) {
            metrics.put(op, new Metric());
        }
    }
    
    /**
     * 设置是否启用监控
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    /**
     * 检查是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * 记录操作
     * @param operation 操作类型
     * @param timeMs 耗时（毫秒）
     * @param success 是否成功
     */
    public void record(Operation operation, long timeMs, boolean success) {
        if (!enabled) {
            return;
        }
        
        Metric metric = metrics.get(operation);
        if (metric != null) {
            metric.record(timeMs, success);
        }
    }
    
    /**
     * 测量操作耗时（辅助方法）
     */
    public long startTimer() {
        return System.currentTimeMillis();
    }
    
    /**
     * 结束计时并记录
     */
    public void endTimer(Operation operation, long startTime, boolean success) {
        if (!enabled) {
            return;
        }
        
        long elapsed = System.currentTimeMillis() - startTime;
        record(operation, elapsed, success);
    }
    
    /**
     * 获取操作统计信息
     */
    public String getOperationStats(Operation operation) {
        Metric metric = metrics.get(operation);
        if (metric == null || metric.count.get() == 0) {
            return operation.name() + ": 无数据";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(operation.name()).append(":\n");
        sb.append("  - 总次数: ").append(metric.count.get()).append("\n");
        sb.append("  - 平均耗时: ").append(String.format("%.2f", metric.getAverage())).append("ms\n");
        sb.append("  - 最大耗时: ").append(metric.maxTime.get()).append("ms\n");
        sb.append("  - 最小耗时: ").append(metric.minTime.get() == Long.MAX_VALUE ? 0 : metric.minTime.get()).append("ms\n");
        sb.append("  - 错误率: ").append(String.format("%.2f%%", metric.getErrorRate())).append("\n");
        
        return sb.toString();
    }
    
    /**
     * 获取所有操作的统计信息
     */
    public String getAllStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 性能监控报告 ===\n");
        sb.append("运行时间: ").append((System.currentTimeMillis() - startTime) / 1000).append("秒\n\n");
        
        for (Operation op : Operation.values()) {
            Metric metric = metrics.get(op);
            if (metric != null && metric.count.get() > 0) {
                sb.append(getOperationStats(op)).append("\n");
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 获取吞吐量（每秒操作数）
     */
    public double getThroughput(Operation operation) {
        Metric metric = metrics.get(operation);
        if (metric == null) {
            return 0.0;
        }
        
        long runningTime = (System.currentTimeMillis() - startTime) / 1000;
        if (runningTime == 0) {
            return 0.0;
        }
        
        return (double) metric.count.get() / runningTime;
    }
    
    /**
     * 获取总体吞吐量
     */
    public double getTotalThroughput() {
        int totalCount = 0;
        for (Metric metric : metrics.values()) {
            totalCount += metric.count.get();
        }
        
        long runningTime = (System.currentTimeMillis() - startTime) / 1000;
        if (runningTime == 0) {
            return 0.0;
        }
        
        return (double) totalCount / runningTime;
    }
    
    /**
     * 重置所有统计数据
     */
    public void reset() {
        for (Metric metric : metrics.values()) {
            metric.totalTime.set(0);
            metric.count.set(0);
            metric.maxTime.set(0);
            metric.minTime.set(Long.MAX_VALUE);
            metric.errorCount.set(0);
        }
        logger.i(TAG, "性能监控数据已重置");
    }
    
    /**
     * 打印报告
     */
    public void printReport() {
        logger.i(TAG, "\n" + getAllStats());
    }
    
    /**
     * 获取简要统计
     */
    public String getSummary() {
        int totalOps = 0;
        int totalErrors = 0;
        
        for (Metric metric : metrics.values()) {
            totalOps += metric.count.get();
            totalErrors += metric.errorCount.get();
        }
        
        double errorRate = totalOps > 0 ? (double) totalErrors / totalOps * 100 : 0.0;
        
        return String.format("总操作: %d, 总错误: %d (%.2f%%), 吞吐量: %.2f ops/s",
            totalOps, totalErrors, errorRate, getTotalThroughput());
    }
}


