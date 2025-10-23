package game.app.anticheat;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Hook框架检测器
 * 检测Xposed、Frida、Substrate等Hook框架
 */
public class HookDetector {
    
    private final Context context;
    private final List<String> detectedMethods = new ArrayList<>();
    
    // Xposed相关包名
    private static final String[] XPOSED_PACKAGES = {
        "de.robv.android.xposed.installer",
        "de.robv.android.xposed",
        "io.va.exposed",
        "com.saurik.substrate",
        "com.topjohnwu.magisk"
    };
    
    // Hook框架特征文件
    private static final String[] HOOK_FILES = {
        "/system/lib/libxposed_art.so",
        "/system/lib64/libxposed_art.so",
        "/system/xposed.prop",
        "/system/framework/XposedBridge.jar",
        "/system/lib/libsubstrate.so",
        "/system/lib/libsubstrate-dvm.so",
        "/system/lib/libsubstrate.dylib"
    };
    
    // Frida相关库
    private static final String[] FRIDA_LIBRARIES = {
        "frida-agent",
        "frida-gadget",
        "frida-server"
    };
    
    public HookDetector(Context context) {
        this.context = context.getApplicationContext();
    }
    
    /**
     * 检测Hook框架
     */
    public boolean detectHookFramework() {
        detectedMethods.clear();
        
        return detectXposed()
            || detectSubstrate()
            || detectFrida()
            || detectXposedByStack()
            || detectXposedByClassLoader()
            || detectVirtualXposed();
    }
    
    /**
     * 检测Xposed
     */
    private boolean detectXposed() {
        // 检查Xposed包
        for (String packageName : XPOSED_PACKAGES) {
            try {
                context.getPackageManager().getPackageInfo(packageName, 0);
                detectedMethods.add("检测到Xposed包: " + packageName);
                return true;
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        
        // 检查Xposed文件
        for (String file : HOOK_FILES) {
            if (new File(file).exists()) {
                detectedMethods.add("检测到Hook文件: " + file);
                return true;
            }
        }
        
        // 检查Xposed环境变量
        try {
            throw new Exception("检测Xposed");
        } catch (Exception e) {
            for (StackTraceElement stackTraceElement : e.getStackTrace()) {
                if (stackTraceElement.getClassName().contains("de.robv.android.xposed")) {
                    detectedMethods.add("堆栈检测到Xposed");
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * 通过ClassLoader检测Xposed
     */
    private boolean detectXposedByClassLoader() {
        try {
            Set<String> libraries = new HashSet<>();
            String mapsFilename = "/proc/" + android.os.Process.myPid() + "/maps";
            BufferedReader reader = new BufferedReader(new FileReader(mapsFilename));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.endsWith(".so") || line.endsWith(".jar")) {
                    int n = line.lastIndexOf(" ");
                    libraries.add(line.substring(n + 1));
                }
            }
            reader.close();
            
            for (String library : libraries) {
                if (library.contains("xposed") || library.contains("XposedBridge")) {
                    detectedMethods.add("内存中检测到Xposed库: " + library);
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }
    
    /**
     * 通过堆栈检测Xposed
     */
    private boolean detectXposedByStack() {
        try {
            throw new Exception("Xposed检测");
        } catch (Exception e) {
            int zygoteInitCallCount = 0;
            for (StackTraceElement item : e.getStackTrace()) {
                if ("com.android.internal.os.ZygoteInit".equals(item.getClassName())) {
                    zygoteInitCallCount++;
                    if (zygoteInitCallCount == 2) {
                        detectedMethods.add("堆栈异常:检测到Xposed");
                        return true;
                    }
                }
                
                if ("com.saurik.substrate.MS$2".equals(item.getClassName()) &&
                    "invoked".equals(item.getMethodName())) {
                    detectedMethods.add("堆栈检测到Substrate");
                    return true;
                }
                
                if ("de.robv.android.xposed.XposedBridge".equals(item.getClassName())) {
                    detectedMethods.add("堆栈检测到XposedBridge");
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * 检测Substrate
     */
    private boolean detectSubstrate() {
        try {
            Class.forName("com.saurik.substrate.MS$2");
            detectedMethods.add("检测到Substrate框架");
            return true;
        } catch (ClassNotFoundException ignored) {
        }
        return false;
    }
    
    /**
     * 检测Frida
     */
    private boolean detectFrida() {
        // 检查Frida库
        try {
            Set<String> libraries = new HashSet<>();
            String mapsFilename = "/proc/" + android.os.Process.myPid() + "/maps";
            BufferedReader reader = new BufferedReader(new FileReader(mapsFilename));
            String line;
            
            while ((line = reader.readLine()) != null) {
                for (String fridaLib : FRIDA_LIBRARIES) {
                    if (line.toLowerCase().contains(fridaLib.toLowerCase())) {
                        detectedMethods.add("检测到Frida库: " + fridaLib);
                        reader.close();
                        return true;
                    }
                }
            }
            reader.close();
        } catch (Exception ignored) {
        }
        
        // 检查Frida端口
        if (checkFridaPort()) {
            detectedMethods.add("检测到Frida默认端口");
            return true;
        }
        
        // 检查Frida线程
        if (checkFridaThreads()) {
            detectedMethods.add("检测到Frida线程");
            return true;
        }
        
        return false;
    }
    
    /**
     * 检查Frida端口
     */
    private boolean checkFridaPort() {
        try {
            // Frida默认端口27042
            File file = new File("/proc/net/tcp");
            if (file.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(file));
                String line;
                while ((line = reader.readLine()) != null) {
                    // 检查27042端口 (十六进制 69CA)
                    if (line.contains(":69CA")) {
                        reader.close();
                        return true;
                    }
                }
                reader.close();
            }
        } catch (Exception ignored) {
        }
        return false;
    }
    
    /**
     * 检查Frida线程
     */
    private boolean checkFridaThreads() {
        try {
            Set<Thread> threads = Thread.getAllStackTraces().keySet();
            for (Thread thread : threads) {
                String name = thread.getName();
                if (name != null && (name.contains("frida") || name.contains("gum-js-loop"))) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }
    
    /**
     * 检测Virtual Xposed
     */
    private boolean detectVirtualXposed() {
        try {
            // Virtual Xposed特征
            String packageName = context.getPackageName();
            ApplicationInfo appInfo = context.getPackageManager()
                .getApplicationInfo(packageName, 0);
            
            if (appInfo.dataDir.startsWith("/data/user/") && 
                !appInfo.dataDir.contains(packageName)) {
                detectedMethods.add("检测到Virtual Xposed环境");
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }
    
    /**
     * 获取Hook详情
     */
    public String getHookDetails() {
        if (detectedMethods.isEmpty()) {
            return "未检测到Hook框架";
        }
        return String.join("; ", detectedMethods);
    }
}

