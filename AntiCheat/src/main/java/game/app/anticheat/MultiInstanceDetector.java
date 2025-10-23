package game.app.anticheat;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 多开检测器
 * 检测应用是否运行在虚拟环境中（如平行空间、双开助手等）
 */
public class MultiInstanceDetector {
    
    private final Context context;
    private final List<String> detectedMethods = new ArrayList<>();
    
    // 已知的多开应用包名
    private static final String[] VIRTUAL_APP_PACKAGES = {
        "com.lbe.parallel",           // 平行空间
        "com.excelliance.dualaid",    // 双开助手
        "com.lody.virtual",           // VirtualApp
        "com.qihoo.magic",            // 360分身大师
        "com.dual.dualspace",         // 双开大师
        "com.jiubang.commerce.gomultiple", // GO多开
        "com.ludashi.dualspace",      // 鲁大师双开
        "com.trigtech.privateme",     // 隐私大师
        "com.excelliance.multiaccounts" // 多账号
    };
    
    // 虚拟环境特征路径
    private static final String[] VIRTUAL_PATHS = {
        "/data/data/com.lbe.parallel",
        "/data/data/com.lody.virtual",
        "/data/user/"
    };
    
    public MultiInstanceDetector(Context context) {
        this.context = context.getApplicationContext();
    }
    
    /**
     * 检测是否运行在虚拟环境
     */
    public boolean isRunningInVirtualApp() {
        detectedMethods.clear();
        
        return checkVirtualAppInstalled()
            || checkDataPath()
            || checkMultiProcesses()
            || checkAppDir()
            || checkSystemProperties();
    }
    
    /**
     * 检查是否安装了多开应用
     */
    private boolean checkVirtualAppInstalled() {
        for (String packageName : VIRTUAL_APP_PACKAGES) {
            try {
                context.getPackageManager().getPackageInfo(packageName, 0);
                detectedMethods.add("检测到多开应用: " + packageName);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }
    
    /**
     * 检查数据路径
     */
    private boolean checkDataPath() {
        try {
            String dataPath = context.getApplicationInfo().dataDir;
            String packageName = context.getPackageName();
            
            // 正常路径应该是 /data/data/包名 或 /data/user/0/包名
            if (!dataPath.contains(packageName)) {
                detectedMethods.add("数据路径异常: " + dataPath);
                return true;
            }
            
            // 检查是否包含虚拟环境特征
            for (String virtualPath : VIRTUAL_PATHS) {
                if (dataPath.startsWith(virtualPath) && !dataPath.equals("/data/user/0/" + packageName)) {
                    detectedMethods.add("虚拟环境路径: " + dataPath);
                    return true;
                }
            }
            
        } catch (Exception ignored) {
        }
        return false;
    }
    
    /**
     * 检查多进程
     */
    private boolean checkMultiProcesses() {
        try {
            String packageName = context.getPackageName();
            int myUid = android.os.Process.myUid();
            
            BufferedReader reader = new BufferedReader(new FileReader("/proc/self/cgroup"));
            String line;
            int processCount = 0;
            
            while ((line = reader.readLine()) != null) {
                if (line.contains(packageName)) {
                    processCount++;
                }
            }
            reader.close();
            
            // 如果有多个相同包名的进程，可能是多开
            if (processCount > 1) {
                detectedMethods.add("检测到多个进程实例");
                return true;
            }
            
        } catch (Exception ignored) {
        }
        return false;
    }
    
    /**
     * 检查应用目录
     */
    private boolean checkAppDir() {
        try {
            String nativeLibraryDir = context.getApplicationInfo().nativeLibraryDir;
            
            // 虚拟环境的native库目录通常不在正常位置
            if (nativeLibraryDir.contains("arm/nb")) {
                detectedMethods.add("Native库目录异常: " + nativeLibraryDir);
                return true;
            }
            
        } catch (Exception ignored) {
        }
        return false;
    }
    
    /**
     * 检查系统属性
     */
    private boolean checkSystemProperties() {
        try {
            // 检查用户ID
            int uid = android.os.Process.myUid();
            // 虚拟环境的UID通常不在正常范围
            if (uid > 20000 && uid < 50000) {
                // 这个范围可能是虚拟环境
                // 但也可能是正常的多用户，需要结合其他检测
            }
            
            // 检查进程名
            String processName = getProcessName();
            String packageName = context.getPackageName();
            
            if (processName != null && !processName.equals(packageName)) {
                if (processName.contains(":") && !processName.startsWith(packageName)) {
                    detectedMethods.add("进程名异常: " + processName);
                    return true;
                }
            }
            
        } catch (Exception ignored) {
        }
        return false;
    }
    
    /**
     * 获取进程名
     */
    private String getProcessName() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/self/cmdline"));
            String processName = reader.readLine();
            reader.close();
            if (processName != null) {
                processName = processName.trim();
            }
            return processName;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 获取多开详情
     */
    public String getMultiInstanceDetails() {
        if (detectedMethods.isEmpty()) {
            return "未检测到多开环境";
        }
        return String.join("; ", detectedMethods);
    }
}


