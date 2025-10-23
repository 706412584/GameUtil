package game.app.anticheat;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * 反作弊模块使用示例
 */
public class AntiCheatExample extends Activity {
    
    private SecurityDetector securityDetector;
    private TextView resultTextView;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 初始化安全检测器
        securityDetector = new SecurityDetector(this, new SecurityDetector.SecurityCallback() {
            @Override
            public void onThreatDetected(String threatType, String description, int riskLevel) {
                // 检测到威胁时的回调
                runOnUiThread(() -> {
                    String message = String.format("[%s] %s (风险等级: %d/5)", 
                        threatType, description, riskLevel);
                    Toast.makeText(AntiCheatExample.this, message, Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onDetectionComplete(boolean passed, List<SecurityDetector.DetectionResult> threats) {
                // 检测完成时的回调
                runOnUiThread(() -> {
                    handleDetectionResult(passed, threats);
                });
            }
        });
        
        // 开始安全检测
        securityDetector.performSecurityCheck();
    }
    
    /**
     * 处理检测结果
     */
    private void handleDetectionResult(boolean passed, List<SecurityDetector.DetectionResult> threats) {
        int score = securityDetector.getSecurityScore();
        
        if (!passed) {
            // 未通过安全检测
            showSecurityWarning(threats, score);
        } else {
            // 通过安全检测
            Toast.makeText(this, "安全检测通过，分数: " + score, Toast.LENGTH_LONG).show();
            continueNormalFlow();
        }
    }
    
    /**
     * 显示安全警告
     */
    private void showSecurityWarning(List<SecurityDetector.DetectionResult> threats, int score) {
        StringBuilder message = new StringBuilder();
        message.append("安全检测失败！\n\n");
        message.append("安全分数: ").append(score).append("/100\n\n");
        message.append("检测到以下威胁:\n");
        
        for (SecurityDetector.DetectionResult threat : threats) {
            message.append(String.format("• %s (风险:%d/5)\n  %s\n\n", 
                threat.description, 
                threat.riskLevel,
                threat.details));
        }
        
        // 根据风险等级决定处理方式
        int maxRisk = 0;
        for (SecurityDetector.DetectionResult threat : threats) {
            if (threat.riskLevel > maxRisk) {
                maxRisk = threat.riskLevel;
            }
        }
        
        if (maxRisk >= 5) {
            // 高风险：直接退出
            showCriticalWarningAndExit(message.toString());
        } else if (maxRisk >= 3) {
            // 中等风险：警告但允许继续
            showWarningAndContinue(message.toString());
        } else {
            // 低风险：仅记录
            logWarning(message.toString());
            continueNormalFlow();
        }
    }
    
    /**
     * 显示严重警告并退出
     */
    private void showCriticalWarningAndExit(String message) {
        new AlertDialog.Builder(this)
            .setTitle("严重安全警告")
            .setMessage(message + "\n应用将退出以保护您的账号安全。")
            .setCancelable(false)
            .setPositiveButton("确定", (dialog, which) -> {
                finish();
                System.exit(0);
            })
            .show();
    }
    
    /**
     * 显示警告但允许继续
     */
    private void showWarningAndContinue(String message) {
        new AlertDialog.Builder(this)
            .setTitle("安全警告")
            .setMessage(message + "\n检测到潜在风险，建议在安全环境下运行。")
            .setPositiveButton("我知道了", (dialog, which) -> {
                continueNormalFlow();
            })
            .setNegativeButton("退出", (dialog, which) -> {
                finish();
            })
            .show();
    }
    
    /**
     * 记录警告
     */
    private void logWarning(String message) {
        // 将警告信息上报到服务器
        // reportToServer(message);
        android.util.Log.w("AntiCheat", message);
    }
    
    /**
     * 继续正常流程
     */
    private void continueNormalFlow() {
        // 继续应用的正常启动流程
        Toast.makeText(this, "应用启动中...", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * 高级用法：自定义检测策略
     */
    private void advancedUsage() {
        // 1. 仅检测Root
        RootDetector rootDetector = new RootDetector(this);
        if (rootDetector.isDeviceRooted()) {
            // 处理Root设备
        }
        
        // 2. 仅检测模拟器
        EmulatorDetector emulatorDetector = new EmulatorDetector(this);
        if (emulatorDetector.isEmulator()) {
            // 处理模拟器
        }
        
        // 3. 签名验证
        SignatureValidator signatureValidator = new SignatureValidator(this);
        // 设置期望的签名（正式版本的签名SHA256值）
        signatureValidator.setExpectedSignature("YOUR_SIGNATURE_SHA256_HERE");
        if (!signatureValidator.verifySignature()) {
            // 签名被篡改
        }
        
        // 4. 时间作弊检测
        TimeCheatDetector timeCheatDetector = new TimeCheatDetector(this);
        // 从服务器获取时间后调用
        long serverTime = System.currentTimeMillis(); // 实际应从服务器获取
        timeCheatDetector.setServerTime(serverTime);
        
        // 5. 生成完整安全报告
        String report = securityDetector.generateSecurityReport();
        android.util.Log.i("SecurityReport", report);
    }
}

