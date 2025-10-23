package game.app.anticheat;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 模拟器检测器
 * 检测应用是否运行在模拟器中
 */
public class EmulatorDetector {
    
    private final Context context;
    private final List<String> detectedMethods = new ArrayList<>();
    
    // 模拟器特征文件
    private static final String[] EMULATOR_FILES = {
        "/dev/socket/qemud",
        "/dev/qemu_pipe",
        "/system/lib/libc_malloc_debug_qemu.so",
        "/sys/qemu_trace",
        "/system/bin/qemu-props",
        "/dev/socket/genyd",
        "/dev/socket/baseband_genyd"
    };
    
    // Genymotion特征
    private static final String[] GENYMOTION_FILES = {
        "/dev/socket/genyd",
        "/dev/socket/baseband_genyd"
    };
    
    // 模拟器属性关键词
    private static final String[] EMULATOR_PROPERTIES = {
        "init.svc.qemud",
        "init.svc.qemu-props",
        "qemu.hw.mainkeys",
        "qemu.sf.fake_camera",
        "qemu.sf.lcd_density",
        "ro.bootloader",
        "ro.bootmode",
        "ro.hardware",
        "ro.kernel.android.qemud",
        "ro.kernel.qemu.gles",
        "ro.kernel.qemu",
        "ro.product.device",
        "ro.product.model",
        "ro.product.name",
        "ro.serialno"
    };
    
    public EmulatorDetector(Context context) {
        this.context = context.getApplicationContext();
    }
    
    /**
     * 检测是否为模拟器
     */
    public boolean isEmulator() {
        detectedMethods.clear();
        
        return checkBasicEmulatorProperties()
            || checkEmulatorFiles()
            || checkEmulatorBuild()
            || checkOperatorName()
            || checkCpuInfo()
            || checkGenymotion()
            || checkPipes();
    }
    
    /**
     * 检查基础模拟器属性
     */
    private boolean checkBasicEmulatorProperties() {
        int suspiciousCount = 0;
        
        // Build信息检查
        if (Build.FINGERPRINT.startsWith("generic")
            || Build.FINGERPRINT.startsWith("unknown")
            || Build.MODEL.contains("google_sdk")
            || Build.MODEL.contains("Emulator")
            || Build.MODEL.contains("Android SDK built for x86")
            || Build.MANUFACTURER.contains("Genymotion")
            || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
            || "google_sdk".equals(Build.PRODUCT)) {
            
            detectedMethods.add("Build信息特征: " + Build.MODEL);
            suspiciousCount++;
        }
        
        return suspiciousCount > 0;
    }
    
    /**
     * 检查模拟器文件
     */
    private boolean checkEmulatorFiles() {
        for (String file : EMULATOR_FILES) {
            if (new File(file).exists()) {
                detectedMethods.add("模拟器文件: " + file);
                return true;
            }
        }
        return false;
    }
    
    /**
     * 检查Build信息
     */
    private boolean checkEmulatorBuild() {
        // 检查硬件名称
        if (Build.HARDWARE.equals("goldfish") 
            || Build.HARDWARE.equals("vbox86")
            || Build.HARDWARE.contains("nox")
            || Build.HARDWARE.contains("ttVM_x86")) {
            detectedMethods.add("硬件信息: " + Build.HARDWARE);
            return true;
        }
        
        // 检查主板
        if (Build.BOARD.toLowerCase().contains("nox")) {
            detectedMethods.add("主板信息: " + Build.BOARD);
            return true;
        }
        
        // 检查产品名称
        if (Build.PRODUCT != null && Build.PRODUCT.toLowerCase().contains("nox")) {
            detectedMethods.add("产品信息: " + Build.PRODUCT);
            return true;
        }
        
        return false;
    }
    
    /**
     * 检查运营商名称
     */
    private boolean checkOperatorName() {
        String operatorName = ((android.telephony.TelephonyManager) 
            context.getSystemService(Context.TELEPHONY_SERVICE))
            .getNetworkOperatorName()
            .toLowerCase();
        
        if ("android".equals(operatorName)) {
            detectedMethods.add("运营商名称异常");
            return true;
        }
        return false;
    }
    
    /**
     * 检查CPU信息
     */
    private boolean checkCpuInfo() {
        try {
            String[] args = {"/system/bin/cat", "/proc/cpuinfo"};
            ProcessBuilder cmd = new ProcessBuilder(args);
            
            Process process = cmd.start();
            StringBuilder sb = new StringBuilder();
            String readLine;
            java.io.BufferedReader responseReader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream(), "utf-8"));
            
            while ((readLine = responseReader.readLine()) != null) {
                sb.append(readLine);
            }
            
            responseReader.close();
            String cpuInfo = sb.toString().toLowerCase();
            
            // x86架构通常是模拟器
            if (cpuInfo.contains("intel") || cpuInfo.contains("amd")) {
                detectedMethods.add("CPU架构异常: x86");
                return true;
            }
            
        } catch (Exception ignored) {
        }
        return false;
    }
    
    /**
     * 检查Genymotion
     */
    private boolean checkGenymotion() {
        if (Build.MANUFACTURER.contains("Genymotion")) {
            detectedMethods.add("Genymotion模拟器");
            return true;
        }
        
        for (String file : GENYMOTION_FILES) {
            if (new File(file).exists()) {
                detectedMethods.add("Genymotion文件: " + file);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 检查QEMU管道
     */
    private boolean checkPipes() {
        try {
            String[] args = {"/system/bin/cat", "/proc/self/maps"};
            ProcessBuilder cmd = new ProcessBuilder(args);
            Process process = cmd.start();
            
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()));
            
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("qemu") || line.contains("vbox") || line.contains("ttVM")) {
                    detectedMethods.add("检测到模拟器管道");
                    reader.close();
                    return true;
                }
            }
            reader.close();
        } catch (Exception ignored) {
        }
        return false;
    }
    
    /**
     * 获取模拟器详情
     */
    public String getEmulatorDetails() {
        if (detectedMethods.isEmpty()) {
            return "真实设备";
        }
        return String.join("; ", detectedMethods);
    }
}

