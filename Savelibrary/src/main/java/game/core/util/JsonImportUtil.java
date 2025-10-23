package game.core.util;

import game.core.Logger;
import game.core.SaveData;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * JSON导入工具类
 * 功能：
 * 1. 导入完整JSON创建存档
 * 2. 自动识别并创建主表和分表
 * 3. 支持自定义分表策略
 * 4. JSON格式验证
 * 5. 批量导入支持
 */
public class JsonImportUtil {
    private static final String TAG = "JsonImportUtil";
    
    private final Logger logger;
    
    // 分表策略接口
    public interface TableSplitStrategy {
        /**
         * 判断是否需要分表
         * @param key JSON键名
         * @param value JSON值
         * @return 分表名称，返回null表示放入主表
         */
        String getTableName(String key, Object value);
    }
    
    /**
     * 导入结果
     */
    public static class ImportResult {
        public boolean success;
        public String mainTableData;
        public Map<String, String> subTableData;
        public List<String> errors;
        public long processingTime;
        public int totalKeys;
        public int mainTableKeys;
        public int subTableCount;
        
        public ImportResult() {
            this.success = false;
            this.subTableData = new HashMap<>();
            this.errors = new ArrayList<>();
            this.totalKeys = 0;
            this.mainTableKeys = 0;
            this.subTableCount = 0;
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("导入结果:\n");
            sb.append("- 成功: ").append(success).append("\n");
            sb.append("- 总键数: ").append(totalKeys).append("\n");
            sb.append("- 主表键数: ").append(mainTableKeys).append("\n");
            sb.append("- 分表数量: ").append(subTableCount).append("\n");
            sb.append("- 处理时间: ").append(processingTime).append("ms\n");
            if (!errors.isEmpty()) {
                sb.append("- 错误:\n");
                for (String error : errors) {
                    sb.append("  * ").append(error).append("\n");
                }
            }
            return sb.toString();
        }
    }
    
    /**
     * 默认分表策略：按键名前缀分表
     * 例如：inventory_* -> inventory表, quest_* -> quest表
     */
    public static class PrefixBasedStrategy implements TableSplitStrategy {
        private final Map<String, String> prefixMapping;
        
        public PrefixBasedStrategy() {
            this.prefixMapping = new HashMap<>();
        }
        
        public PrefixBasedStrategy(Map<String, String> prefixMapping) {
            this.prefixMapping = prefixMapping;
        }
        
        public void addMapping(String prefix, String tableName) {
            prefixMapping.put(prefix, tableName);
        }
        
        @Override
        public String getTableName(String key, Object value) {
            for (Map.Entry<String, String> entry : prefixMapping.entrySet()) {
                if (key.startsWith(entry.getKey())) {
                    return entry.getValue();
                }
            }
            return null; // 放入主表
        }
    }
    
    /**
     * 按大小分表策略：大对象自动放入单独的分表
     */
    public static class SizeBasedStrategy implements TableSplitStrategy {
        private final int sizeThreshold;
        
        public SizeBasedStrategy(int sizeThreshold) {
            this.sizeThreshold = sizeThreshold;
        }
        
        @Override
        public String getTableName(String key, Object value) {
            try {
                String valueStr = value.toString();
                if (valueStr.length() > sizeThreshold) {
                    // 大对象放入单独的分表
                    return "large_" + key;
                }
            } catch (Exception e) {
                // 忽略
            }
            return null; // 放入主表
        }
    }
    
    /**
     * 组合策略：支持多个策略组合
     */
    public static class CompositeStrategy implements TableSplitStrategy {
        private final List<TableSplitStrategy> strategies;
        
        public CompositeStrategy() {
            this.strategies = new ArrayList<>();
        }
        
        public void addStrategy(TableSplitStrategy strategy) {
            strategies.add(strategy);
        }
        
        @Override
        public String getTableName(String key, Object value) {
            for (TableSplitStrategy strategy : strategies) {
                String tableName = strategy.getTableName(key, value);
                if (tableName != null) {
                    return tableName;
                }
            }
            return null;
        }
    }
    
    public JsonImportUtil(Logger logger) {
        this.logger = logger != null ? logger : new Logger.DefaultLogger();
    }
    
    /**
     * 验证JSON格式
     */
    public boolean validateJson(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            logger.e(TAG, "JSON字符串为空");
            return false;
        }
        
        try {
            new JSONObject(jsonStr);
            return true;
        } catch (JSONException e) {
            logger.e(TAG, "JSON格式错误: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 导入完整JSON（使用默认策略：不分表）
     */
    public ImportResult importJson(String saveId, String jsonStr) {
        return importJson(saveId, jsonStr, null);
    }
    
    /**
     * 导入完整JSON并自动分表
     * @param saveId 存档ID
     * @param jsonStr JSON字符串
     * @param strategy 分表策略（null表示不分表）
     * @return 导入结果
     */
    public ImportResult importJson(String saveId, String jsonStr, TableSplitStrategy strategy) {
        ImportResult result = new ImportResult();
        long startTime = System.currentTimeMillis();
        
        try {
            logger.i(TAG, "开始导入JSON，存档ID: " + saveId);
            
            // 验证JSON
            if (!validateJson(jsonStr)) {
                result.errors.add("JSON格式验证失败");
                return result;
            }
            
            JSONObject inputJson = new JSONObject(jsonStr);
            
            // 准备主表数据
            JSONObject mainData = new JSONObject();
            Map<String, JSONObject> tableDataMap = new HashMap<>();
            
            // 遍历所有键
            Iterator<String> keys = inputJson.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = inputJson.get(key);
                result.totalKeys++;
                
                // 确定该键应该放入哪个表
                String tableName = null;
                if (strategy != null) {
                    tableName = strategy.getTableName(key, value);
                }
                
                if (tableName == null) {
                    // 放入主表
                    mainData.put(key, value);
                    result.mainTableKeys++;
                } else {
                    // 放入分表
                    if (!tableDataMap.containsKey(tableName)) {
                        tableDataMap.put(tableName, new JSONObject());
                    }
                    tableDataMap.get(tableName).put(key, value);
                }
            }
            
            // 构建主表SaveData格式
            JSONObject mainSaveData = new JSONObject();
            mainSaveData.put("saveId", saveId);
            mainSaveData.put("timestamp", System.currentTimeMillis());
            mainSaveData.put("version", 1);
            mainSaveData.put("data", mainData);
            
            result.mainTableData = mainSaveData.toString(2);
            
            // 构建分表SaveData格式
            for (Map.Entry<String, JSONObject> entry : tableDataMap.entrySet()) {
                String tableName = entry.getKey();
                JSONObject tableData = entry.getValue();
                
                JSONObject tableSaveData = new JSONObject();
                tableSaveData.put("saveId", saveId + "_" + tableName);
                tableSaveData.put("timestamp", System.currentTimeMillis());
                tableSaveData.put("version", 1);
                tableSaveData.put("data", tableData);
                
                result.subTableData.put(tableName, tableSaveData.toString(2));
                result.subTableCount++;
            }
            
            result.success = true;
            logger.i(TAG, "JSON导入成功");
            
        } catch (Exception e) {
            result.success = false;
            result.errors.add("导入失败: " + e.getMessage());
            logger.e(TAG, "导入JSON失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        result.processingTime = System.currentTimeMillis() - startTime;
        logger.i(TAG, result.toString());
        
        return result;
    }
    
    /**
     * 批量导入多个存档
     */
    public List<ImportResult> batchImport(Map<String, String> saveJsonMap, TableSplitStrategy strategy) {
        List<ImportResult> results = new ArrayList<>();
        
        logger.i(TAG, "开始批量导入，数量: " + saveJsonMap.size());
        
        for (Map.Entry<String, String> entry : saveJsonMap.entrySet()) {
            String saveId = entry.getKey();
            String jsonStr = entry.getValue();
            ImportResult result = importJson(saveId, jsonStr, strategy);
            results.add(result);
        }
        
        // 统计
        int successCount = 0;
        int failCount = 0;
        for (ImportResult result : results) {
            if (result.success) {
                successCount++;
            } else {
                failCount++;
            }
        }
        
        logger.i(TAG, "批量导入完成 - 成功: " + successCount + ", 失败: " + failCount);
        
        return results;
    }
    
    /**
     * 从SaveData导出为纯JSON（移除元数据）
     */
    public String exportToPlainJson(SaveData saveData) {
        try {
            JSONObject exportJson = new JSONObject();
            Map<String, Object> data = saveData.getData();
            
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                String key = entry.getKey();
                // 跳过内部元数据
                if (!key.startsWith("_")) {
                    exportJson.put(key, entry.getValue());
                }
            }
            
            return exportJson.toString(2);
            
        } catch (Exception e) {
            logger.e(TAG, "导出JSON失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 合并多个JSON对象
     */
    public String mergeJsonObjects(List<String> jsonStrings) {
        try {
            JSONObject merged = new JSONObject();
            
            for (String jsonStr : jsonStrings) {
                if (jsonStr == null || jsonStr.trim().isEmpty()) {
                    continue;
                }
                
                JSONObject json = new JSONObject(jsonStr);
                Iterator<String> keys = json.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    merged.put(key, json.get(key));
                }
            }
            
            return merged.toString(2);
            
        } catch (Exception e) {
            logger.e(TAG, "合并JSON失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 从JSON路径提取值（支持点号分隔的路径）
     * 例如：player.inventory.gold
     */
    public Object extractValue(String jsonStr, String path) {
        try {
            JSONObject json = new JSONObject(jsonStr);
            String[] parts = path.split("\\.");
            
            Object current = json;
            for (String part : parts) {
                if (current instanceof JSONObject) {
                    current = ((JSONObject) current).get(part);
                } else if (current instanceof JSONArray) {
                    int index = Integer.parseInt(part);
                    current = ((JSONArray) current).get(index);
                } else {
                    return null;
                }
            }
            
            return current;
            
        } catch (Exception e) {
            logger.e(TAG, "提取值失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 计算JSON大小（字节）
     */
    public int calculateJsonSize(String jsonStr) {
        if (jsonStr == null) {
            return 0;
        }
        try {
            return jsonStr.getBytes("UTF-8").length;
        } catch (Exception e) {
            return jsonStr.length();
        }
    }
    
    /**
     * 美化JSON输出
     */
    public String prettifyJson(String jsonStr) {
        try {
            JSONObject json = new JSONObject(jsonStr);
            return json.toString(2);
        } catch (Exception e) {
            logger.e(TAG, "美化JSON失败: " + e.getMessage());
            return jsonStr;
        }
    }
    
    /**
     * 压缩JSON输出（移除空格和换行）
     */
    public String compressJson(String jsonStr) {
        try {
            JSONObject json = new JSONObject(jsonStr);
            return json.toString();
        } catch (Exception e) {
            logger.e(TAG, "压缩JSON失败: " + e.getMessage());
            return jsonStr;
        }
    }
}


