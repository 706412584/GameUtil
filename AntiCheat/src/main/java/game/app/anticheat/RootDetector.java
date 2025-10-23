package game.app.anticheat;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Root检测器
 * 检测设备是否被Root
 */
public class RootDetector {
    
    private final Context context;
    private final List<String> detectedMethods = new ArrayList<>();
    
    // Root相关文件路径
    private static final String[] SU_PATHS = {
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system/su",
        "/system/bin/.ext/.su",
        "/system/usr/we-need-root/su-backup",
        "/system/xbin/mu",
        "/data/local/su",
        "/data/local/bin/su",
        "/data/local/xbin/su"
    };
    
    // Root管理应用包名
    private static final String[] ROOT_APPS = {
        "com.noshufou.android.su",
        "com.noshufou.android.su.elite",
        "eu.chainfire.supersu",
        "com.koushikdutta.superuser",
        "com.thirdparty.superuser",
        "com.yellowes.su",
        "com.topjohnwu.magisk",
        "me.weishu.kernelsu"
    };
    
    // Root相关文件
    private static final String[] ROOT_CLOAKING_PACKAGES = {
        "com.devadvance.rootcloak",
        "com.devadvance.rootcloakplus",
        "de.robv.android.xposed.installer",
        "com.saurik.substrate",
        "com.zachspong.temprootremovejb",
        "com.ramdroid.appquarantine"
    };
    
    public RootDetector(Context context) {
        this.context = context.getApplicationContext();
    }
    
    /**
     * 检测设备是否Root
     */
    public boolean isDeviceRooted() {
        detectedMethods.clear();
        
        return checkSuExists() 
            || checkRootAppInstalled() 
            || checkRootCloakingApps()
            || checkBusyBox()
            || checkTestKeys()
            || checkDangerousProperties()
            || checkRWPaths();
    }
    
    /**
     * 检查su命令
     */
    private boolean checkSuExists() {
        for (String path : SU_PATHS) {
            if (new File(path).exists()) {
                detectedMethods.add("检测到su文件: " + path);
                return true;
            }
        }
        return false;
    }
    
    /**
     * 检查Root管理应用
     */
    private boolean checkRootAppInstalled() {
        for (String packageName : ROOT_APPS) {
            try {
                context.getPackageManager().getPackageInfo(packageName, 0);
                detectedMethods.add("检测到Root应用: " + packageName);
                return true;
            } catch (Exception e) {
                // 未安装
            }
        }
        return false;
    }
    
    /**
     * 检查Root隐藏应用
     */
    private boolean checkRootCloakingApps() {
        for (String packageName : ROOT_CLOAKING_PACKAGES) {
            try {
                context.getPackageManager().getPackageInfo(packageName, 0);
                detectedMethods.add("检测到Root隐藏应用: " + packageName);
                return true;
            } catch (Exception e) {
                // 未安装
            }
        }
        return false;
    }
    
    /**
     * 检查BusyBox
     */
    private boolean checkBusyBox() {
        String[] paths = {"/system/xbin/busybox", "/system/bin/busybox"};
        for (String path : paths) {
            if (new File(path).exists()) {
                detectedMethods.add("检测到BusyBox: " + path);
                return true;
            }
        }
        return false;
    }
    
    /**
     * 检查Test-Keys
     */
    private boolean checkTestKeys() {
        String buildTags = android.os.Build.TAGS;
        if (buildTags != null && buildTags.contains("test-keys")) {
            detectedMethods.add("检测到test-keys");
            return true;
        }
        return false;
    }
    
    /**
     * 检查危险属性
     */
    private boolean checkDangerousProperties() {
        String[] props = {
            "ro.debuggable",
            "ro.secure"
        };
        
        for (String prop : props) {
            String value = getSystemProperty(prop);
            if ("1".equals(value) && "ro.debuggable".equals(prop)) {
                detectedMethods.add("检测到可调试属性");
                return true;
            }
            if ("0".equals(value) && "ro.secure".equals(prop)) {
                detectedMethods.add("检测到不安全属性");
                return true;
            }
        }
        return false;
    }
    
    /**
     * 检查可读写的系统目录
     */
    private boolean checkRWPaths() {
        String[] paths = {"/system", "/system/bin", "/system/sbin", "/system/xbin", "/vendor/bin", "/sbin", "/etc"};
        
        for (String path : paths) {
            File file = new File(path);
            if (file.exists() && file.canWrite()) {
                detectedMethods.add("系统目录可写: " + path);
                return true;
            }
        }
        return false;
    }
    
    /**
     * 获取系统属性
     */
    private String getSystemProperty(String key) {
        String value = null;
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method get = c.getMethod("get", String.class);
            value = (String) get.invoke(c, key);
        } catch (Exception ignored) {
        }
        return value;
    }
    
    /**
     * 执行su命令检测
     */
    private boolean canExecuteSuCommand() {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"/system/xbin/which", "su"});
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = in.readLine();
            if (line != null) {
                detectedMethods.add("su命令可执行");
                return true;
            }
        } catch (Exception ignored) {
        } finally {
            if (process != null) process.destroy();
        }
        return false;
    }
    
    /**
     * 获取Root详情
     */
    public String getRootDetails() {
        if (detectedMethods.isEmpty()) {
            return "未检测到Root";
        }
        return String.join("; ", detectedMethods);
    }
}


