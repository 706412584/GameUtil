package game.core;

import org.json.JSONObject;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
/**
 * 反作弊管理器（支持自定义验证）
 * 功能：
 * 1. 存档完整性校验（SHA-256校验和）
 * 2. 存档防复制覆盖（唯一设备ID + 时间戳验证）
 * 3. 自定义验证器支持（如用户账号验证）
 * 4. 元数据缓存管理
 */
public class AntiCheatManager {
    private static final String TAG = "AntiCheatManager";
    
    private final Logger logger;
    
    // 存储每个存档的元数据[7]
    private final Map<String, SaveMetadata> metadataCache = new HashMap<>();
    
    // 自定义验证器接口[3]
    public interface CustomValidator {
        /**
         * 验证存档数据
         * @param saveData 存档数据
         * @param context 验证上下文（设备ID、用户账号等）
         * @return 验证通过返回true，否则返回false
         */
        boolean validate(SaveData saveData, ValidationContext context);
        
        /**
         * 获取验证器名称
         */
        String getName();
        
        /**
         * 获取验证失败的错误信息
         */
        String getErrorMessage();
    }
    
    // 自定义验证器注册表
    private final Map<String, CustomValidator> customValidators = new HashMap<>();
    
    public AntiCheatManager(Logger logger) {
        this.logger = logger != null ? logger : new Logger.DefaultLogger();
    }
    
    /**
     * 存档元数据类
     */
    private static class SaveMetadata {
        String deviceId;
        String userAccount;
        long createTime;
        long lastModifyTime;
        int modifyCount;
        
        SaveMetadata(String deviceId) {
            this.deviceId = deviceId;
            this.createTime = System.currentTimeMillis();
            this.lastModifyTime = this.createTime;
            this.modifyCount = 0;
        }
        
        SaveMetadata(String deviceId, String userAccount) {
            this.deviceId = deviceId;
            this.userAccount = userAccount;
            this.createTime = System.currentTimeMillis();
            this.lastModifyTime = this.createTime;
            this.modifyCount = 0;
        }
    }
    
    /**
     * 验证上下文类[3]
     */
    public static class ValidationContext {
        private final String deviceId;
        private final String userAccount;
        private final long currentTime;
        private final Map<String, Object> customAttributes;
        
        public ValidationContext(String deviceId, String userAccount) {
            this.deviceId = deviceId;
            this.userAccount = userAccount;
            this.currentTime = System.currentTimeMillis();
            this.customAttributes = new HashMap<>();
        }
        
        // Getter方法
        public String getDeviceId() { return deviceId; }
        public String getUserAccount() { return userAccount; }
        public long getCurrentTime() { return currentTime; }
        public Map<String, Object> getCustomAttributes() { return customAttributes; }
        
        public Object getAttribute(String key) {
            return customAttributes.get(key);
        }
    }
    
    /**
     * 注册自定义验证器[3]
     */
    public void registerValidator(String validatorId, CustomValidator validator) {
        synchronized (customValidators) {
            customValidators.put(validatorId, validator);
            logger.i(TAG, "注册自定义验证器: " + validatorId);
        }
    }
    
    /**
     * 移除自定义验证器
     */
    public void unregisterValidator(String validatorId) {
        synchronized (customValidators) {
            customValidators.remove(validatorId);
            logger.i(TAG, "移除自定义验证器: " + validatorId);
        }
    }
    
    /**
     * 执行所有自定义验证
     */
    private boolean executeCustomValidations(SaveData saveData, String deviceId, String userAccount) {
        synchronized (customValidators) {
            if (customValidators.isEmpty()) {
                return true;
            }
            
            ValidationContext context = new ValidationContext(deviceId, userAccount);
            boolean allValid = true;
            
            for (Map.Entry<String, CustomValidator> entry : customValidators.entrySet()) {
                CustomValidator validator = entry.getValue();
                try {
                    if (!validator.validate(saveData, context)) {
                        logger.w(TAG, "自定义验证失败 [" + entry.getKey() + "]: " + validator.getErrorMessage());
                        allValid = false;
                    }
                } catch (Exception e) {
                    logger.e(TAG, "自定义验证器执行异常 [" + entry.getKey() + "]: " + e.getMessage());
                    allValid = false;
                }
            }
            
            return allValid;
        }
    }
    
    /**
     * 生成数据的SHA-256校验和[2,5]
     */
    public String generateChecksum(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes("UTF-8"));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            logger.e(TAG, "生成校验和失败: " + e.getMessage());
            return "";
        }
    }
    
    /**
     * 验证数据完整性[2]
     */
    public boolean verifyChecksum(String data, String expectedChecksum) {
        if (expectedChecksum == null || expectedChecksum.isEmpty()) {
            logger.w(TAG, "校验和为空，跳过验证");
            return true;
        }
        
        String actualChecksum = generateChecksum(data);
        boolean isValid = actualChecksum.equals(expectedChecksum);
        
        if (!isValid) {
            logger.e(TAG, "校验和验证失败！");
            logger.e(TAG, "期望: " + expectedChecksum);
            logger.e(TAG, "实际: " + actualChecksum);
        }
        
        return isValid;
    }
    
    /**
     * 为存档数据添加完整性校验[5]
     */
    public String addChecksumToSaveData(String jsonData) {
        try {
            JSONObject json = new JSONObject(jsonData);
            
            // 移除旧的校验和（如果存在）
            json.remove("_checksum");
            
            // 生成数据的校验和（不包括校验和字段本身）
            String dataWithoutChecksum = json.toString();
            String checksum = generateChecksum(dataWithoutChecksum);
            
            // 添加校验和
            json.put("_checksum", checksum);
            
            return json.toString();
        } catch (Exception e) {
            logger.e(TAG, "添加校验和失败: " + e.getMessage());
            return jsonData;
        }
    }
    
    /**
     * 验证存档数据的完整性
     */
    public boolean verifySaveData(String jsonData) {
        try {
            JSONObject json = new JSONObject(jsonData);
            
            String checksum = json.optString("_checksum", "");
            if (checksum.isEmpty()) {
                logger.w(TAG, "存档数据没有校验和，跳过验证");
                return true;
            }
            
            // 移除校验和字段
            json.remove("_checksum");
            
            // 验证
            String dataWithoutChecksum = json.toString();
            return verifyChecksum(dataWithoutChecksum, checksum);
            
        } catch (Exception e) {
            logger.e(TAG, "验证存档数据失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取或生成设备唯一ID[5]
     */
    public String getDeviceId(File deviceIdFile) {
        try {
            if (deviceIdFile.exists()) {
                // 读取现有ID
                FileReader reader = new FileReader(deviceIdFile);
                char[] buffer = new char[36]; // UUID长度
                int bytesRead = reader.read(buffer);
                reader.close();
                
                if (bytesRead > 0) {
                    return new String(buffer, 0, bytesRead).trim();
                }
            }
            
            // 生成新ID
            String deviceId = UUID.randomUUID().toString();
            FileWriter writer = new FileWriter(deviceIdFile);
            writer.write(deviceId);
            writer.close();
            
            logger.i(TAG, "生成新的设备ID: " + deviceId);
            return deviceId;
            
        } catch (Exception e) {
            logger.e(TAG, "获取设备ID失败: " + e.getMessage());
            return "unknown_device_" + System.currentTimeMillis();
        }
    }
    
    /**
     * 检测存档是否被非法复制[1,3]
     */
    public boolean detectCopyCheat(SaveData saveData, String currentDeviceId) {
        return detectCopyCheat(saveData, currentDeviceId, null);
    }
    
    /**
     * 检测存档是否被非法复制（增强版，支持用户账号验证）
     */
    public boolean detectCopyCheat(SaveData saveData, String currentDeviceId, String currentUserAccount) {
        String saveId = saveData.getSaveId();
        
        // 从存档中读取设备ID和用户账号
        String saveDeviceId = saveData.getString("_deviceId", "");
        String saveUserAccount = saveData.getString("_userAccount", "");
        long saveCreateTime = saveData.getLong("_createTime", 0);
        long saveModifyTime = saveData.getLong("_modifyTime", 0);
        int saveModifyCount = saveData.getInt("_modifyCount", 0);
        
        // 如果是新存档，初始化元数据
        if (saveDeviceId.isEmpty()) {
            saveData.set("_deviceId", currentDeviceId);
            if (currentUserAccount != null) {
                saveData.set("_userAccount", currentUserAccount);
            }
            saveData.set("_createTime", System.currentTimeMillis());
            saveData.set("_modifyTime", System.currentTimeMillis());
            saveData.set("_modifyCount", 0);
            return false; // 新存档，无作弊
        }
        
        // 检查设备ID是否匹配
        if (!saveDeviceId.equals(currentDeviceId)) {
            logger.w(TAG, "检测到存档复制！设备ID不匹配");
            logger.w(TAG, "存档设备ID: " + saveDeviceId);
            logger.w(TAG, "当前设备ID: " + currentDeviceId);
            return true;
        }
        
        // 检查用户账号是否匹配（如果提供了当前用户账号）
        if (currentUserAccount != null && !saveUserAccount.isEmpty() && 
            !saveUserAccount.equals(currentUserAccount)) {
            logger.w(TAG, "检测到用户账号不匹配！");
            logger.w(TAG, "存档用户账号: " + saveUserAccount);
            logger.w(TAG, "当前用户账号: " + currentUserAccount);
            return true;
        }
        
        // 检查时间戳是否异常
        if (saveModifyTime < saveCreateTime) {
            logger.w(TAG, "检测到时间戳异常！修改时间早于创建时间");
            return true;
        }
        
        // 检查修改计数是否异常
        SaveMetadata metadata = metadataCache.get(saveId);
        if (metadata != null && saveModifyCount < metadata.modifyCount) {
            logger.w(TAG, "检测到修改计数异常！可能是旧存档覆盖");
            return true;
        }
        
        // 更新缓存
        if (metadata == null) {
            metadata = new SaveMetadata(currentDeviceId, currentUserAccount);
            metadataCache.put(saveId, metadata);
        }
        metadata.lastModifyTime = saveModifyTime;
        metadata.modifyCount = saveModifyCount;
        
        // 执行自定义验证
        if (!executeCustomValidations(saveData, currentDeviceId, currentUserAccount)) {
            logger.w(TAG, "自定义验证检测到作弊行为");
            return true;
        }
        
        return false; // 未检测到作弊
    }
    
    /**
     * 更新存档的反作弊元数据
     */
    public void updateSaveMetadata(SaveData saveData, String deviceId) {
        updateSaveMetadata(saveData, deviceId, null);
    }
    
    /**
     * 更新存档的反作弊元数据（增强版，支持用户账号）
     */
    public void updateSaveMetadata(SaveData saveData, String deviceId, String userAccount) {
        int modifyCount = saveData.getInt("_modifyCount", 0);
        
        saveData.set("_deviceId", deviceId);
        if (userAccount != null) {
            saveData.set("_userAccount", userAccount);
        }
        saveData.set("_modifyTime", System.currentTimeMillis());
        saveData.set("_modifyCount", modifyCount + 1);
        
        // 更新缓存
        SaveMetadata metadata = metadataCache.get(saveData.getSaveId());
        if (metadata == null) {
            metadata = new SaveMetadata(deviceId, userAccount);
            metadataCache.put(saveData.getSaveId(), metadata);
        }
        metadata.lastModifyTime = System.currentTimeMillis();
        metadata.modifyCount = modifyCount + 1;
        metadata.userAccount = userAccount;
    }
    
    /**
     * 清除缓存
     */
    public void clearCache() {
        metadataCache.clear();
        customValidators.clear();
    }
    
    /**
     * 内置用户账号验证器[3]
     */
    public static class UserAccountValidator implements CustomValidator {
        @Override
        public boolean validate(SaveData saveData, ValidationContext context) {
            String saveAccount = saveData.getString("_userAccount", "");
            String currentAccount = context.getUserAccount();
            
            if (currentAccount == null || currentAccount.isEmpty()) {
                return true; // 没有当前账号信息时跳过验证
            }
            
            if (saveAccount.isEmpty()) {
                // 存档中没有账号信息，可能是旧存档，自动更新
                saveData.set("_userAccount", currentAccount);
                return true;
            }
            
            return saveAccount.equals(currentAccount);
        }
        
        @Override
        public String getName() {
            return "用户账号验证器";
        }
        
        @Override
        public String getErrorMessage() {
            return "用户账号不匹配";
        }
    }
    
    /**
     * 内置时间戳验证器（防止时间篡改）[2]
     */
    public static class TimestampValidator implements CustomValidator {
        private final long maxAllowedTimeDifference;
        
        public TimestampValidator(long maxAllowedTimeDifference) {
            this.maxAllowedTimeDifference = maxAllowedTimeDifference;
        }
        
        @Override
        public boolean validate(SaveData saveData, ValidationContext context) {
            long saveModifyTime = saveData.getLong("_modifyTime", 0);
            long currentTime = context.getCurrentTime();
            long timeDifference = Math.abs(currentTime - saveModifyTime);
            
            return timeDifference <= maxAllowedTimeDifference;
        }
        
        @Override
        public String getName() {
            return "时间戳验证器";
        }
        
        @Override
        public String getErrorMessage() {
            return "存档时间戳异常，可能被篡改";
        }
    }
}
