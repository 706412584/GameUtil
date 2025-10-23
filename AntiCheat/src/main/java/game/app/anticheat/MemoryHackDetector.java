package game.app.anticheat;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 内存修改器检测器
 * 检测GameGuardian、CheatEngine等内存修改工具
 */
public class MemoryHackDetector {
    
    private final Context context;
    private final List<String> detectedMethods = new ArrayList<>();
    
    // 已知的内存修改工具包名
    private static final String[] MEMORY_HACK_PACKAGES = {
        "catch_.me_.if_.you_.can_",      // GameGuardian混淆包名
        "com.guarddev.safefolder",       // GameGuardian
        "com.cheatengine.gamehack",
        "com.sbtools.gamehack.gg",
        "com.freedom.frd",                // Freedom
        "cc.forestapp",                   // Forest（LuckyPatcher）
        "org.creeplays.hack",
        "com.cih.game_cih",               // CIH
        "com.charles.lpoqwert",           // GameKiller
        "com.gmd.hidesoftkeys"            // GMD GestureControl
    };
    
    // 内存修改工具相关文件
    private static final String[] MEMORY_HACK_FILES = {
        "/data/data/catch_.me_.if_.you_.can_",
        "/data/app/catch_.me_.if_.you_.can_",
        "/sdcard/GameGuardian",
        "/sdcard/ggsaves"
    };
    
    // 内存修改工具库文件
    private static final String[] MEMORY_HACK_LIBS = {
        "libgameguardian",
        "libsubstrate",
        "libcheatengine"
    };
    
    public MemoryHackDetector(Context context) {
        this.context = context.getApplicationContext();
    }
    
    /**
     * 检测内存修改工具
     */
    public boolean detectMemoryHackTools() {
        detectedMethods.clear();
        
        return checkMemoryHackApps()
            || checkMemoryHackFiles()
            || checkMemoryHackLibs()
            || checkSuspiciousProcesses()
            || checkMemoryMaps();
    }
    
    /**
     * 检查内存修改应用
     */
    private boolean checkMemoryHackApps() {
        for (String packageName : MEMORY_HACK_PACKAGES) {
            try {
                context.getPackageManager().getPackageInfo(packageName, 0);
                detectedMethods.add("检测到内存修改工具: " + packageName);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }
    
    /**
     * 检查内存修改相关文件
     */
    private boolean checkMemoryHackFiles() {
        for (String filePath : MEMORY_HACK_FILES) {
            if (new File(filePath).exists()) {
                detectedMethods.add("检测到修改工具文件: " + filePath);
                return true;
            }
        }
        return false;
    }
    
    /**
     * 检查内存修改库
     */
    private boolean checkMemoryHackLibs() {
        try {
            String mapsFile = "/proc/" + android.os.Process.myPid() + "/maps";
            BufferedReader reader = new BufferedReader(new FileReader(mapsFile));
            String line;
            
            while ((line = reader.readLine()) != null) {
                for (String lib : MEMORY_HACK_LIBS) {
                    if (line.toLowerCase().contains(lib.toLowerCase())) {
                        detectedMethods.add("检测到修改工具库: " + lib);
                        reader.close();
                        return true;
                    }
                }
            }
            reader.close();
            
        } catch (Exception ignored) {
        }
        return false;
    }
    
    /**
     * 检查可疑进程
     */
    private boolean checkSuspiciousProcesses() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/self/status"));
            String line;
            
            while ((line = reader.readLine()) != null) {
                // 检查是否有可疑的进程附加
                if (line.startsWith("TracerPid:")) {
                    String[] parts = line.split(":");
                    if (parts.length > 1) {
                        int pid = Integer.parseInt(parts[1].trim());
                        if (pid != 0) {
                            // 有进程在跟踪当前进程
                            String tracerName = getProcessName(pid);
                            if (tracerName != null && isSuspiciousProcess(tracerName)) {
                                detectedMethods.add("检测到可疑跟踪进程: " + tracerName);
                                reader.close();
                                return true;
                            }
                        }
                    }
                }
            }
            reader.close();
            
        } catch (Exception ignored) {
        }
        return false;
    }
    
    /**
     * 检查内存映射
     */
    private boolean checkMemoryMaps() {
        try {
            String mapsFile = "/proc/" + android.os.Process.myPid() + "/maps";
            BufferedReader reader = new BufferedReader(new FileReader(mapsFile));
            String line;
            int anonymousCount = 0;
            
            while ((line = reader.readLine()) != null) {
                // 检查匿名内存映射（内存修改器常用）
                if (line.contains("[anon:") || line.endsWith("[anon]")) {
                    anonymousCount++;
                }
                
                // 检查可疑的内存区域
                if (line.contains("/data/local/tmp/") || 
                    line.contains("/dev/ashmem/") && line.contains("gg")) {
                    detectedMethods.add("检测到可疑内存映射");
                    reader.close();
                    return true;
                }
            }
            reader.close();
            
            // 如果匿名内存映射过多，可能是内存修改器
            if (anonymousCount > 1000) {
                detectedMethods.add("异常的匿名内存映射数量: " + anonymousCount);
                return true;
            }
            
        } catch (Exception ignored) {
        }
        return false;
    }
    
    /**
     * 获取进程名
     */
    private String getProcessName(int pid) {
        try {
            BufferedReader reader = new BufferedReader(
                new FileReader("/proc/" + pid + "/cmdline"));
            String processName = reader.readLine();
            reader.close();
            if (processName != null) {
                processName = processName.trim().replace("\0", "");
            }
            return processName;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 判断是否为可疑进程
     */
    private boolean isSuspiciousProcess(String processName) {
        if (processName == null) return false;
        
        String lowerName = processName.toLowerCase();
        return lowerName.contains("gg") 
            || lowerName.contains("gameguardian")
            || lowerName.contains("cheat")
            || lowerName.contains("hack")
            || lowerName.contains("gdb")
            || lowerName.contains("lldb");
    }
    
    /**
     * 获取内存修改详情
     */
    public String getMemoryHackDetails() {
        if (detectedMethods.isEmpty()) {
            return "未检测到内存修改工具";
        }
        return String.join("; ", detectedMethods);
    }
}

