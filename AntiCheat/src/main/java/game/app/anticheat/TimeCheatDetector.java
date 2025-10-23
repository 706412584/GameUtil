package game.app.anticheat;

import android.content.Context;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;

/**
 * 时间作弊检测器
 * 检测系统时间是否被修改
 */
public class TimeCheatDetector {
    
    private final Context context;
    private final List<String> detectedMethods = new ArrayList<>();
    
    private long lastServerTime = 0;
    private long lastLocalTime = 0;
    
    // 允许的时间偏差（毫秒）
    private static final long ALLOWED_TIME_DIFF = 5000; // 5秒
    
    public TimeCheatDetector(Context context) {
        this.context = context.getApplicationContext();
    }
    
    /**
     * 检测时间作弊
     */
    public boolean detectTimeManipulation() {
        detectedMethods.clear();
        
        return checkSystemTimeJump()
            || checkBootTimeJump()
            || checkServerTimeSync();
    }
    
    /**
     * 检查系统时间跳跃
     */
    private boolean checkSystemTimeJump() {
        long currentTime = System.currentTimeMillis();
        long elapsedRealtime = SystemClock.elapsedRealtime();
        
        if (lastLocalTime > 0) {
            long timeDiff = currentTime - lastLocalTime;
            long realtimeDiff = elapsedRealtime - getLastElapsedRealtime();
            
            // 如果系统时间差异与真实运行时间差异过大
            if (Math.abs(timeDiff - realtimeDiff) > ALLOWED_TIME_DIFF) {
                detectedMethods.add(String.format(
                    "系统时间跳跃: 时间差=%dms, 实际差=%dms", 
                    timeDiff, realtimeDiff));
                return true;
            }
        }
        
        lastLocalTime = currentTime;
        saveLastElapsedRealtime(elapsedRealtime);
        
        return false;
    }
    
    /**
     * 检查启动时间跳跃
     */
    private boolean checkBootTimeJump() {
        long bootTime = System.currentTimeMillis() - SystemClock.elapsedRealtime();
        long savedBootTime = getSavedBootTime();
        
        if (savedBootTime > 0) {
            // 启动时间不应该改变（除非重启）
            long bootTimeDiff = Math.abs(bootTime - savedBootTime);
            if (bootTimeDiff > ALLOWED_TIME_DIFF) {
                detectedMethods.add(String.format(
                    "设备启动时间异常: 差异=%dms", bootTimeDiff));
                return true;
            }
        }
        
        saveBootTime(bootTime);
        return false;
    }
    
    /**
     * 检查服务器时间同步
     * （需要应用主动调用 setServerTime 设置服务器时间）
     */
    private boolean checkServerTimeSync() {
        if (lastServerTime > 0) {
            long currentTime = System.currentTimeMillis();
            long expectedTime = lastServerTime + (SystemClock.elapsedRealtime() - getLastServerElapsedTime());
            
            long diff = Math.abs(currentTime - expectedTime);
            if (diff > ALLOWED_TIME_DIFF) {
                detectedMethods.add(String.format(
                    "服务器时间不同步: 差异=%dms", diff));
                return true;
            }
        }
        return false;
    }
    
    /**
     * 设置服务器时间（由应用在获取服务器时间后调用）
     */
    public void setServerTime(long serverTime) {
        this.lastServerTime = serverTime;
        saveLastServerElapsedTime(SystemClock.elapsedRealtime());
    }
    
    /**
     * 获取时间偏差（毫秒）
     */
    public long getTimeDifference() {
        if (lastServerTime > 0) {
            long currentTime = System.currentTimeMillis();
            long expectedTime = lastServerTime + 
                (SystemClock.elapsedRealtime() - getLastServerElapsedTime());
            return currentTime - expectedTime;
        }
        return 0;
    }
    
    /**
     * 保存启动时间
     */
    private void saveBootTime(long bootTime) {
        context.getSharedPreferences("anti_cheat", Context.MODE_PRIVATE)
            .edit()
            .putLong("boot_time", bootTime)
            .apply();
    }
    
    /**
     * 获取保存的启动时间
     */
    private long getSavedBootTime() {
        return context.getSharedPreferences("anti_cheat", Context.MODE_PRIVATE)
            .getLong("boot_time", 0);
    }
    
    /**
     * 保存上次运行时间
     */
    private void saveLastElapsedRealtime(long time) {
        context.getSharedPreferences("anti_cheat", Context.MODE_PRIVATE)
            .edit()
            .putLong("last_elapsed_realtime", time)
            .apply();
    }
    
    /**
     * 获取上次运行时间
     */
    private long getLastElapsedRealtime() {
        return context.getSharedPreferences("anti_cheat", Context.MODE_PRIVATE)
            .getLong("last_elapsed_realtime", 0);
    }
    
    /**
     * 保存服务器时间对应的运行时间
     */
    private void saveLastServerElapsedTime(long time) {
        context.getSharedPreferences("anti_cheat", Context.MODE_PRIVATE)
            .edit()
            .putLong("last_server_elapsed_time", time)
            .apply();
    }
    
    /**
     * 获取服务器时间对应的运行时间
     */
    private long getLastServerElapsedTime() {
        return context.getSharedPreferences("anti_cheat", Context.MODE_PRIVATE)
            .getLong("last_server_elapsed_time", 0);
    }
    
    /**
     * 获取时间作弊详情
     */
    public String getTimeCheatDetails() {
        if (detectedMethods.isEmpty()) {
            return "未检测到时间修改";
        }
        return String.join("; ", detectedMethods);
    }
}


