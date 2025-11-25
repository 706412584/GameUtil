package game.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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
     * 获取数据 (原始对象)
     */
    public Object get(String key) {
        return data.get(key);
    }

    /**
     * 获取指定类型的数据 (泛型方法)
     * @param key 键
     * @param type 目标类型的Class
     * @return 对应类型的值，如果类型不匹配或不存在返回null
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = data.get(key);
        if (value == null) {
            return null;
        }

        if (type.isInstance(value)) {
            return (T) value;
        }

        // 处理数字类型的转换
        if (Number.class.isAssignableFrom(type) && value instanceof Number) {
            Number num = (Number) value;
            if (type == Integer.class) return (T) Integer.valueOf(num.intValue());
            if (type == Long.class) return (T) Long.valueOf(num.longValue());
            if (type == Double.class) return (T) Double.valueOf(num.doubleValue());
            if (type == Float.class) return (T) Float.valueOf(num.floatValue());
            if (type == Short.class) return (T) Short.valueOf(num.shortValue());
            if (type == Byte.class) return (T) Byte.valueOf(num.byteValue());
        }

        // 处理JSON类型转换
        if (type == JSONObject.class) {
            try {
                if (value instanceof String) {
                    return (T) new JSONObject((String) value);
                }
                if (value instanceof Map) {
                    return (T) new JSONObject((Map<?, ?>) value);
                }
            } catch (Exception e) {
                // 转换失败
            }
        }


        if (type == JSONArray.class) {
            try {
                if (value instanceof String) {
                    return (T) new JSONArray((String) value);
                }
                if (value instanceof List) {
                    return (T) new JSONArray((List<?>) value);
                }
            } catch (Exception e) {
                // 转换失败
            }
        }

        // 处理String转换
        if (type == String.class) {
            return (T) value.toString();
        }

        return null;
    }

    /**
     * 获取指定类型的数据，带默认值
     */
    public <T> T get(String key, Class<T> type, T defaultValue) {
        T value = get(key, type);
        return value != null ? value : defaultValue;
    }
    
    /**
     * 获取字符串数据
     */
    public String getString(String key, String defaultValue) {
        return get(key, String.class, defaultValue);
    }
    
    /**
     * 获取整数数据
     */
    public int getInt(String key, int defaultValue) {
        Integer value = get(key, Integer.class);
        return value != null ? value : defaultValue;
    }
    
    /**
     * 获取长整数数据
     */
    public long getLong(String key, long defaultValue) {
        Long value = get(key, Long.class);
        return value != null ? value : defaultValue;
    }
    
    /**
     * 获取浮点数据
     */
    public double getDouble(String key, double defaultValue) {
        Double value = get(key, Double.class);
        return value != null ? value : defaultValue;
    }
    
    /**
     * 获取布尔数据
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        Boolean value = get(key, Boolean.class);
        return value != null ? value : defaultValue;
    }

    /**
     * 获取JSONObject
     */
    public JSONObject getJSONObject(String key) {
        return get(key, JSONObject.class);
    }

    /**
     * 获取JSONArray
     */
    public JSONArray getJSONArray(String key) {
        return get(key, JSONArray.class);
    }

    /**
     * 获取List
     * 注意：这将尝试将JSONArray转换为List，或者返回现有的List
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> getList(String key, Class<T> itemType) {
        Object value = data.get(key);
        if (value == null) {
            return new ArrayList<>();
        }

        List<T> list = new ArrayList<>();
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                Object item = array.opt(i);
                if (itemType.isInstance(item)) {
                    list.add((T) item);
                } else if (itemType == String.class) {
                    list.add((T) String.valueOf(item));
                }
                // 可以根据需要添加更多类型转换逻辑
            }
        } else if (value instanceof List) {
            List<?> rawList = (List<?>) value;
            for (Object item : rawList) {
                if (itemType.isInstance(item)) {
                    list.add((T) item);
                }
            }
        }
        return list;
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

