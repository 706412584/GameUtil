package game.app.anticheat;

import android.content.Context;
import android.os.Debug;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 调试器检测器
 * 检测应用是否正在被调试
 */
public class DebugDetector {
    
    private final Context context;
    private final List<String> detectedMethods = new ArrayList<>();
    
    // 调试器端口范围
    private static final int[] DEBUG_PORTS = {5555, 5554, 5037, 23946};
    
    public DebugDetector(Context context) {
        this.context = context.getApplicationContext();
    }
    
    /**
     * 检测是否正在调试
     */
    public boolean isBeingDebugged() {
        detectedMethods.clear();
        
        return checkDebuggerConnected()
            || checkDebugFlag()
            || checkTracerPid()
            || checkDebugPort()
            || checkAdbEnabled();
    }
    
    /**
     * 检查调试器连接
     */
    private boolean checkDebuggerConnected() {
        if (Debug.isDebuggerConnected()) {
            detectedMethods.add("调试器已连接");
            return true;
        }
        return false;
    }
    
    /**
     * 检查Debug标志
     */
    private boolean checkDebugFlag() {
        int flags = context.getApplicationInfo().flags;
        if ((flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            detectedMethods.add("应用处于可调试模式");
            return true;
        }
        return false;
    }
    
    /**
     * 检查TracerPid
     */
    private boolean checkTracerPid() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/self/status"));
            String line;
            
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("TracerPid:")) {
                    String[] parts = line.split(":");
                    if (parts.length > 1) {
                        int tracerPid = Integer.parseInt(parts[1].trim());
                        if (tracerPid != 0) {
                            detectedMethods.add("检测到TracerPid: " + tracerPid);
                            reader.close();
                            return true;
                        }
                    }
                    break;
                }
            }
            reader.close();
        } catch (Exception ignored) {
        }
        return false;
    }
    
    /**
     * 检查调试端口
     */
    private boolean checkDebugPort() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/net/tcp"));
            String line;
            
            while ((line = reader.readLine()) != null) {
                for (int port : DEBUG_PORTS) {
                    String hexPort = Integer.toHexString(port).toUpperCase();
                    if (line.contains(":" + hexPort)) {
                        detectedMethods.add("检测到调试端口: " + port);
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
     * 检查ADB是否启用
     */
    private boolean checkAdbEnabled() {
        try {
            int adbEnabled = android.provider.Settings.Secure.getInt(
                context.getContentResolver(),
                android.provider.Settings.Global.ADB_ENABLED,
                0
            );
            
            if (adbEnabled == 1) {
                detectedMethods.add("ADB调试已启用");
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }
    
    /**
     * 获取调试详情
     */
    public String getDebugDetails() {
        if (detectedMethods.isEmpty()) {
            return "未检测到调试器";
        }
        return String.join("; ", detectedMethods);
    }
}


