package com.example;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.util.List;

import game.app.anticheat.SecurityDetector;
import game.core.GameSaveManager;
import game.core.SaveData;

/**
 * 集成示例：结合安全检测和存档管理
 * 
 * 功能流程：
 * 1. 应用启动时进行安全检测
 * 2. 通过检测后允许读取存档
 * 3. 定期进行后台安全检测
 * 4. 检测到威胁时采取相应措施
 */
public class IntegratedSecurityExample extends Activity {
    
    private static final String TAG = "IntegratedSecurity";
    
    private SecurityDetector securityDetector;
    private GameSaveManager saveManager;
    
    private boolean securityCheckPassed = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 显示启动画面
        showSplashScreen();
        
        // 执行安全检测
        performSecurityCheck();
    }
    
    /**
     * 执行安全检测
     */
    private void performSecurityCheck() {
        Log.i(TAG, "开始安全检测...");
        
        securityDetector = new SecurityDetector(this, 
            new SecurityDetector.SecurityCallback() {
            
            @Override
            public void onThreatDetected(String threatType, 
                                       String description, 
                                       int riskLevel) {
                // 实时威胁通知
                Log.w(TAG, String.format(
                    "检测到威胁: [%s] %s (风险等级: %d/5)", 
                    threatType, description, riskLevel
                ));
                
                // 高风险威胁立即处理
                if (riskLevel >= 4) {
                    handleHighRiskThreat(threatType, description);
                }
            }
            
            @Override
            public void onDetectionComplete(boolean passed, 
                                           List<SecurityDetector.DetectionResult> threats) {
                // 检测完成
                Log.i(TAG, "安全检测完成，结果: " + (passed ? "通过" : "失败"));
                
                runOnUiThread(() -> {
                    handleSecurityResult(passed, threats);
                });
            }
        });
        
        // 执行检测（异步）
        securityDetector.performSecurityCheck();
    }
    
    /**
     * 处理高风险威胁（实时）
     */
    private void handleHighRiskThreat(String type, String description) {
        runOnUiThread(() -> {
            switch (type) {
                case "ROOT":
                    // Root设备：记录并警告
                    Log.e(TAG, "检测到Root设备");
                    reportToServer("ROOT", description, 5);
                    break;
                    
                case "HOOK":
                    // Hook框架：立即退出
                    Log.e(TAG, "检测到Hook框架，应用将退出");
                    reportToServer("HOOK", description, 5);
                    showCriticalErrorAndExit("检测到Hook框架");
                    break;
                    
                case "MEMORY_HACK":
                    // 内存修改器：立即退出
                    Log.e(TAG, "检测到内存修改工具，应用将退出");
                    reportToServer("MEMORY_HACK", description, 5);
                    showCriticalErrorAndExit("检测到作弊工具");
                    break;
                    
                case "SIGNATURE":
                    // 签名异常：立即退出
                    Log.e(TAG, "签名验证失败，应用将退出");
                    reportToServer("SIGNATURE", description, 5);
                    showCriticalErrorAndExit("应用签名异常");
                    break;
                    
                case "MULTI_INSTANCE":
                    // 多开：限制功能
                    Log.w(TAG, "检测到多开环境");
                    reportToServer("MULTI_INSTANCE", description, 4);
                    break;
            }
        });
    }
    
    /**
     * 处理安全检测结果（汇总）
     */
    private void handleSecurityResult(boolean passed, 
                                      List<SecurityDetector.DetectionResult> threats) {
        // 获取安全分数
        int score = securityDetector.getSecurityScore();
        Log.i(TAG, "安全分数: " + score + "/100");
        
        // 生成报告
        String report = securityDetector.generateSecurityReport();
        Log.d(TAG, "安全报告:\n" + report);
        
        // 上报服务器
        uploadSecurityReport(report, score);
        
        // 根据分数决定策略
        if (score >= 80) {
            // 高分：正常运行
            securityCheckPassed = true;
            initializeGame();
            
        } else if (score >= 60) {
            // 中等分数：警告后继续
            showSecurityWarning(threats, score);
            securityCheckPassed = true;
            initializeGame();
            
        } else if (score >= 40) {
            // 低分：严格警告，限制功能
            showSevereWarning(threats, score);
            securityCheckPassed = true;
            initializeGameWithRestrictions();
            
        } else {
            // 极低分：拒绝运行
            securityCheckPassed = false;
            showSecurityErrorAndExit(threats, score);
        }
    }
    
    /**
     * 初始化游戏（正常模式）
     */
    private void initializeGame() {
        Log.i(TAG, "初始化游戏（正常模式）");
        
        // 初始化存档管理器
        File saveDir = new File(getFilesDir(), "saves");
        saveManager = new GameSaveManager(saveDir);
        
        // 启用性能优化
        saveManager.setPerformanceMonitorEnabled(true);
        saveManager.setCompressionEnabled(true);
        
        // 加载存档
        loadGameSave();
        
        // 启动定期安全检测
        startPeriodicSecurityCheck();
        
        // 进入主界面
        Toast.makeText(this, "安全检测通过，欢迎游戏！", Toast.LENGTH_SHORT).show();
        startMainActivity();
    }
    
    /**
     * 初始化游戏（受限模式）
     */
    private void initializeGameWithRestrictions() {
        Log.w(TAG, "初始化游戏（受限模式）");
        
        // 初始化存档管理器
        File saveDir = new File(getFilesDir(), "saves");
        saveManager = new GameSaveManager(saveDir);
        
        // 限制功能
        // - 禁用在线功能
        // - 禁用支付功能
        // - 禁用排行榜
        
        Toast.makeText(this, 
            "检测到安全风险，部分功能已限制", 
            Toast.LENGTH_LONG).show();
        
        startMainActivity();
    }
    
    /**
     * 加载游戏存档
     */
    private void loadGameSave() {
        try {
            // 尝试加载存档
            SaveData saveData = saveManager.loadSave("player_001");
            
            if (saveData != null) {
                Log.i(TAG, "存档加载成功");
                // 应用存档数据
                applySaveData(saveData);
            } else {
                Log.i(TAG, "未找到存档，创建新存档");
                createNewSave();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "存档加载失败: " + e.getMessage());
            createNewSave();
        }
    }
    
    /**
     * 应用存档数据
     */
    private void applySaveData(SaveData saveData) {
        // 从存档中读取数据
        String playerName = saveData.get("player_name", "玩家");
        int level = saveData.get("level", 1);
        int gold = saveData.get("gold", 0);
        
        Log.i(TAG, String.format(
            "玩家: %s, 等级: %d, 金币: %d", 
            playerName, level, gold
        ));
        
        // 应用到游戏中
        // ...
    }
    
    /**
     * 创建新存档
     */
    private void createNewSave() {
        SaveData saveData = new SaveData("player_001");
        saveData.set("player_name", "新玩家");
        saveData.set("level", 1);
        saveData.set("gold", 100);
        saveData.set("create_time", System.currentTimeMillis());
        
        try {
            saveManager.saveSave(saveData);
            Log.i(TAG, "新存档创建成功");
        } catch (Exception e) {
            Log.e(TAG, "新存档创建失败: " + e.getMessage());
        }
    }
    
    /**
     * 启动定期安全检测
     */
    private void startPeriodicSecurityCheck() {
        // 每5分钟检测一次
        android.os.Handler handler = new android.os.Handler(getMainLooper());
        Runnable checkTask = new Runnable() {
            @Override
            public void run() {
                performBackgroundSecurityCheck();
                handler.postDelayed(this, 5 * 60 * 1000);
            }
        };
        handler.postDelayed(checkTask, 5 * 60 * 1000);
        
        Log.i(TAG, "定期安全检测已启动");
    }
    
    /**
     * 后台安全检测
     */
    private void performBackgroundSecurityCheck() {
        new Thread(() -> {
            Log.d(TAG, "执行后台安全检测");
            
            SecurityDetector detector = new SecurityDetector(this, 
                new SecurityDetector.SecurityCallback() {
                
                @Override
                public void onThreatDetected(String type, String desc, int level) {
                    if (level >= 5) {
                        // 严重威胁：立即处理
                        Log.e(TAG, "后台检测到严重威胁: " + type);
                        handleHighRiskThreat(type, desc);
                    }
                }
                
                @Override
                public void onDetectionComplete(boolean passed, 
                                               List<SecurityDetector.DetectionResult> threats) {
                    int score = detector.getSecurityScore();
                    Log.d(TAG, "后台检测完成，分数: " + score);
                    
                    if (score < 40) {
                        // 分数过低：强制退出
                        runOnUiThread(() -> {
                            showCriticalErrorAndExit("安全环境异常");
                        });
                    }
                }
            });
            
            detector.performSecurityCheck();
        }).start();
    }
    
    /**
     * 显示安全警告
     */
    private void showSecurityWarning(List<SecurityDetector.DetectionResult> threats, 
                                    int score) {
        StringBuilder message = new StringBuilder();
        message.append("检测到以下安全风险:\n\n");
        
        for (SecurityDetector.DetectionResult threat : threats) {
            if (threat.detected && threat.riskLevel >= 3) {
                message.append(String.format("• %s\n", threat.description));
            }
        }
        
        message.append(String.format("\n安全分数: %d/100", score));
        
        new AlertDialog.Builder(this)
            .setTitle("安全提示")
            .setMessage(message.toString())
            .setPositiveButton("我知道了", null)
            .show();
    }
    
    /**
     * 显示严重警告
     */
    private void showSevereWarning(List<SecurityDetector.DetectionResult> threats, 
                                  int score) {
        StringBuilder message = new StringBuilder();
        message.append("检测到严重安全风险:\n\n");
        
        for (SecurityDetector.DetectionResult threat : threats) {
            if (threat.detected) {
                message.append(String.format(
                    "• %s (风险:%d/5)\n", 
                    threat.description, 
                    threat.riskLevel
                ));
            }
        }
        
        message.append(String.format(
            "\n安全分数: %d/100\n\n部分功能将被限制", 
            score
        ));
        
        new AlertDialog.Builder(this)
            .setTitle("严重安全警告")
            .setMessage(message.toString())
            .setCancelable(false)
            .setPositiveButton("继续", (dialog, which) -> {
                // 继续但受限
            })
            .setNegativeButton("退出", (dialog, which) -> {
                finish();
            })
            .show();
    }
    
    /**
     * 显示安全错误并退出
     */
    private void showSecurityErrorAndExit(List<SecurityDetector.DetectionResult> threats, 
                                         int score) {
        StringBuilder message = new StringBuilder();
        message.append("安全检测失败，应用无法运行。\n\n");
        message.append(String.format("安全分数: %d/100\n\n", score));
        message.append("检测到的问题:\n");
        
        for (SecurityDetector.DetectionResult threat : threats) {
            if (threat.detected) {
                message.append(String.format("• %s\n", threat.description));
            }
        }
        
        new AlertDialog.Builder(this)
            .setTitle("安全错误")
            .setMessage(message.toString())
            .setCancelable(false)
            .setPositiveButton("退出", (dialog, which) -> {
                finish();
                System.exit(0);
            })
            .show();
    }
    
    /**
     * 显示严重错误并退出
     */
    private void showCriticalErrorAndExit(String reason) {
        new AlertDialog.Builder(this)
            .setTitle("严重错误")
            .setMessage(reason + "\n\n应用将退出以保护您的账号安全。")
            .setCancelable(false)
            .setPositiveButton("确定", (dialog, which) -> {
                finish();
                System.exit(0);
            })
            .show();
    }
    
    /**
     * 上报到服务器
     */
    private void reportToServer(String type, String description, int level) {
        // 构造上报数据
        String deviceId = android.provider.Settings.Secure.getString(
            getContentResolver(),
            android.provider.Settings.Secure.ANDROID_ID
        );
        
        Log.i(TAG, String.format(
            "上报服务器: 类型=%s, 描述=%s, 等级=%d, 设备=%s",
            type, description, level, deviceId
        ));
        
        // 实际项目中这里调用服务器API
        // API.reportSecurity(type, description, level, deviceId);
    }
    
    /**
     * 上传安全报告
     */
    private void uploadSecurityReport(String report, int score) {
        Log.d(TAG, "上传安全报告，分数: " + score);
        
        // 实际项目中这里调用服务器API
        // API.uploadSecurityReport(report, score);
    }
    
    /**
     * 显示启动画面
     */
    private void showSplashScreen() {
        // 显示启动画面
    }
    
    /**
     * 启动主界面
     */
    private void startMainActivity() {
        // 启动主界面
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // 释放资源
        if (saveManager != null) {
            saveManager.shutdown();
        }
    }
}

