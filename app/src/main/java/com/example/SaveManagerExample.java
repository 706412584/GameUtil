package com.example;

import android.content.Context;

import game.core.GameSaveManager;
import game.core.SaveData;
import game.core.AsyncSaveManager;
import game.core.PerformanceMonitor;
import game.core.util.JsonImportUtil;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * 游戏存档工具使用示例
 * 展示优化后的各项新功能
 */
public class SaveManagerExample {
    
    private GameSaveManager manager;
    private Context context;
    
    public SaveManagerExample(Context context) {
        this.context = context;
        initializeSaveManager();
    }
    
    /**
     * 1. 初始化存档管理器并启用性能优化
     */
    private void initializeSaveManager() {
        // 创建存档目录
        File saveDir = new File(context.getFilesDir(), "game_saves");
        
        // 初始化管理器
        manager = new GameSaveManager(saveDir);
        
        // 启用性能监控
        manager.setPerformanceMonitorEnabled(true);
        
        // 启用数据压缩（适合大型存档）
        manager.setCompressionEnabled(true);
        manager.setCompressionThreshold(10240); // 10KB以上才压缩
        
        System.out.println("存档管理器初始化完成");
    }
    
    /**
     * 2. 基础存档操作
     */
    public void basicSaveOperations() {
        // 创建存档数据
        SaveData saveData = new SaveData("save_001");
        saveData.set("player_name", "玩家1");
        saveData.set("level", 10);
        saveData.set("gold", 5000);
        saveData.set("exp", 2500);
        
        // 同步保存
        boolean success = manager.saveSave(saveData);
        System.out.println("保存结果: " + success);
        
        // 读取存档
        SaveData loaded = manager.loadSave("save_001");
        if (loaded != null) {
            System.out.println("玩家名: " + loaded.getString("player_name", ""));
            System.out.println("等级: " + loaded.getInt("level", 0));
        }
    }
    
    /**
     * 3. 从JSON导入存档（自动分表）
     */
    public void importFromJson() {
        // 准备游戏数据JSON
        String gameDataJson = "{\n" +
            "  \"player_name\": \"张三\",\n" +
            "  \"level\": 25,\n" +
            "  \"gold\": 10000,\n" +
            "  \"inventory_weapon\": \"神剑\",\n" +
            "  \"inventory_armor\": \"铠甲\",\n" +
            "  \"inventory_potion\": 50,\n" +
            "  \"quest_main_1\": \"完成\",\n" +
            "  \"quest_main_2\": \"进行中\",\n" +
            "  \"quest_side_1\": \"未接受\",\n" +
            "  \"skill_attack\": 100,\n" +
            "  \"skill_defense\": 80,\n" +
            "  \"skill_magic\": 120\n" +
            "}";
        
        // 定义分表策略
        Map<String, String> prefixMapping = new HashMap<>();
        prefixMapping.put("inventory_", "inventory");  // 背包数据
        prefixMapping.put("quest_", "quest");          // 任务数据
        prefixMapping.put("skill_", "skill");          // 技能数据
        
        // 导入JSON并自动分表
        JsonImportUtil.ImportResult result = manager.importFromJsonWithPrefix(
            "save_002",
            gameDataJson,
            prefixMapping
        );
        
        // 查看导入结果
        if (result.success) {
            System.out.println("=== JSON导入成功 ===");
            System.out.println("总键数: " + result.totalKeys);
            System.out.println("主表键数: " + result.mainTableKeys);
            System.out.println("分表数量: " + result.subTableCount);
            System.out.println("处理耗时: " + result.processingTime + "ms");
        } else {
            System.out.println("导入失败: " + result.errors);
        }
    }
    
    /**
     * 4. 异步保存（不阻塞主线程）
     */
    public void asyncSaveOperations() {
        SaveData saveData = new SaveData("save_003");
        saveData.set("player_name", "李四");
        saveData.set("level", 15);
        
        // 异步保存
        manager.saveAsync(saveData, new AsyncSaveManager.SaveCallback() {
            @Override
            public void onSuccess(String saveId) {
                System.out.println("异步保存成功: " + saveId);
                // 可以在这里更新UI
            }
            
            @Override
            public void onFailure(String saveId, Exception error) {
                System.out.println("异步保存失败: " + saveId);
                System.out.println("错误: " + error.getMessage());
                // 可以在这里显示错误提示
            }
        });
        
        System.out.println("异步保存已提交，不阻塞主线程");
    }
    
    /**
     * 5. 批量操作（高性能）
     */
    public void batchOperations() {
        // 创建多个存档
        java.util.List<SaveData> saveList = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            SaveData data = new SaveData("batch_save_" + i);
            data.set("index", i);
            data.set("value", "数据" + i);
            saveList.add(data);
        }
        
        // 批量异步保存
        System.out.println("开始批量保存 10 个存档...");
        AsyncSaveManager.BatchResult result = manager.batchSaveAsync(saveList);
        
        System.out.println("=== 批量保存结果 ===");
        System.out.println("总数: " + result.total);
        System.out.println("成功: " + result.success);
        System.out.println("失败: " + result.failed);
        System.out.println("总耗时: " + result.totalTime + "ms");
        
        // 批量读取
        java.util.List<String> saveIds = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            saveIds.add("batch_save_" + i);
        }
        
        System.out.println("开始批量读取 10 个存档...");
        java.util.List<SaveData> loadedList = manager.batchLoadAsync(saveIds);
        System.out.println("成功读取: " + loadedList.size() + " 个存档");
    }
    
    /**
     * 6. 使用大小策略自动分表（大对象分离）
     */
    public void importWithSizeStrategy() {
        // 模拟一个包含大量数据的JSON
        String largeJson = "{\n" +
            "  \"player_name\": \"王五\",\n" +
            "  \"level\": 30,\n" +
            "  \"large_map_data\": \"" + generateLargeString(15000) + "\",\n" +
            "  \"small_config\": \"配置数据\"\n" +
            "}";
        
        // 使用大小策略：超过10KB的字段会自动放入单独的分表
        JsonImportUtil.ImportResult result = manager.importFromJsonWithSize(
            "save_004",
            largeJson,
            10240  // 10KB阈值
        );
        
        if (result.success) {
            System.out.println("=== 大小策略导入成功 ===");
            System.out.println("自动创建了 " + result.subTableCount + " 个分表");
            System.out.println("大数据已分离存储");
        }
    }
    
    /**
     * 7. 查看性能统计
     */
    public void viewPerformanceStats() {
        System.out.println("\n=== 完整系统统计 ===");
        System.out.println(manager.getSystemStats());
        
        // 查看性能报告
        System.out.println("\n=== 性能报告 ===");
        manager.printPerformanceReport();
        
        // 查看缓存统计
        System.out.println("\n=== 缓存统计 ===");
        System.out.println(manager.getCacheStats());
        System.out.println("缓存命中率: " + 
            String.format("%.2f%%", manager.getCacheHitRate() * 100));
    }
    
    /**
     * 8. 清理资源
     */
    public void cleanup() {
        // 等待所有异步任务完成
        boolean allCompleted = manager.waitForAsyncTasks(5000);
        System.out.println("异步任务完成: " + allCompleted);
        
        // 关闭管理器
        manager.shutdown();
        System.out.println("存档管理器已关闭");
    }
    
    /**
     * 完整示例流程
     */
    public void runCompleteExample() {
        System.out.println("========== 游戏存档工具示例 ==========\n");
        
        // 1. 基础操作
        System.out.println("--- 1. 基础存档操作 ---");
        basicSaveOperations();
        
        // 2. JSON导入
        System.out.println("\n--- 2. JSON导入（自动分表） ---");
        importFromJson();
        
        // 3. 异步保存
        System.out.println("\n--- 3. 异步保存 ---");
        asyncSaveOperations();
        
        // 4. 批量操作
        System.out.println("\n--- 4. 批量操作 ---");
        batchOperations();
        
        // 5. 大小策略
        System.out.println("\n--- 5. 大小策略导入 ---");
        importWithSizeStrategy();
        
        // 6. 性能统计
        System.out.println("\n--- 6. 性能统计 ---");
        viewPerformanceStats();
        
        // 7. 清理
        System.out.println("\n--- 7. 清理资源 ---");
        cleanup();
        
        System.out.println("\n========== 示例执行完成 ==========");
    }
    
    // 辅助方法：生成指定长度的字符串
    private String generateLargeString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append((char) ('A' + (i % 26)));
        }
        return sb.toString();
    }
}


