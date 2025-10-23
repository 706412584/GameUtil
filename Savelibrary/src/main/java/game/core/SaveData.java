package game.core;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 游戏存档数据模型
 * 支持任意JSON数据结构
 */
public class SaveData {
    private String saveId;
    private long timestamp;
    private long version;
    private final Map<String, Object> data;
    private String checksum; // 用于完整性校验
    
    public SaveData(String saveId) {
        this.saveId = saveId;
        this.timestamp = System.currentTimeMillis();
        this.version = 1;
        this.data = new HashMap<>();
    }
    
    public SaveData(String saveId, JSONObject jsonObject) throws JSONException {
        this.saveId = saveId;
        this.timestamp = jsonObject.optLong("timestamp", System.currentTimeMillis());
        this.version = jsonObject.optLong("version", 1);
        this.checksum = jsonObject.optString("checksum", "");
        this.data = new HashMap<>();
        
        JSONObject dataObj = jsonObject.optJSONObject("data");
        if (dataObj != null) {
            Iterator<String> keys = dataObj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                data.put(key, dataObj.get(key));
            }
        }
    }
    
    /**
     * 设置数据
     */
    public void set(String key, Object value) {
        data.put(key, value);
        timestamp = System.currentTimeMillis();
    }
    
    /**
     * 获取数据
     */
    public Object get(String key) {
        return data.get(key);
    }
    
    /**
     * 获取字符串数据
     */
    public String getString(String key, String defaultValue) {
        Object value = data.get(key);
        return value != null ? value.toString() : defaultValue;
    }
    
    /**
     * 获取整数数据
     */
    public int getInt(String key, int defaultValue) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
    
    /**
     * 获取长整数数据
     */
    public long getLong(String key, long defaultValue) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return defaultValue;
    }
    
    /**
     * 获取浮点数据
     */
    public double getDouble(String key, double defaultValue) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }
    
    /**
     * 获取布尔数据
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = data.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }
    
    /**
     * 移除数据
     */
    public void remove(String key) {
        data.remove(key);
        timestamp = System.currentTimeMillis();
    }
    
    /**
     * 清空所有数据
     */
    public void clear() {
        data.clear();
        timestamp = System.currentTimeMillis();
    }
    
    /**
     * 转换为JSON对象
     */
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("saveId", saveId);
        json.put("timestamp", timestamp);
        json.put("version", version);
        json.put("checksum", checksum);
        
        JSONObject dataObj = new JSONObject();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            dataObj.put(entry.getKey(), entry.getValue());
        }
        json.put("data", dataObj);
        
        return json;
    }
    
    /**
     * 转换为JSON字符串
     */
    public String toJsonString() {
        try {
            return toJson().toString(2); // 格式化输出
        } catch (JSONException e) {
            e.printStackTrace();
            return "{}";
        }
    }
    
    // Getters and Setters
    public String getSaveId() {
        return saveId;
    }
    
    public void setSaveId(String saveId) {
        this.saveId = saveId;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public long getVersion() {
        return version;
    }
    
    public void setVersion(long version) {
        this.version = version;
    }
    
    public Map<String, Object> getData() {
        return data;
    }
    
    public String getChecksum() {
        return checksum;
    }
    
    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }
    
    @Override
    public String toString() {
        return toJsonString();
    }
}

