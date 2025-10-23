# Android 反作弊模块

一个强大的 Android 应用安全检测库，用于检测常见的破解、作弊和篡改行为。

## 功能特性

### 核心检测功能

1. **Root 检测** ✅
   - 检测 su 命令
   - 检测 Root 管理应用（SuperSU、Magisk、KernelSU等）
   - 检测 Root 隐藏工具
   - 检测 Test-Keys
   - 检测系统目录可写性

2. **模拟器检测** ✅
   - 检测 Android SDK 模拟器
   - 检测 Genymotion
   - 检测夜神、雷电、逍遥等国产模拟器
   - 检测 x86 架构
   - 检测 QEMU 特征

3. **Hook 框架检测** ✅
   - 检测 Xposed
   - 检测 Frida
   - 检测 Substrate
   - 检测 VirtualXposed
   - 检测内存中的Hook库

4. **调试器检测** ✅
   - 检测调试器连接
   - 检测 TracerPid
   - 检测 ADB 调试
   - 检测调试端口

5. **签名验证** ✅
   - 验证应用签名
   - 检测二次打包
   - 验证证书有效期
   - 检测 Debug 证书

6. **代理检测** ✅
   - 检测系统代理
   - 检测 HTTP 代理
   - 检测 VPN 连接

7. **多开检测** ✅
   - 检测平行空间
   - 检测双开助手
   - 检测虚拟环境
   - 检测数据路径异常

8. **内存修改器检测** ✅
   - 检测 GameGuardian
   - 检测 Cheat Engine
   - 检测 GameKiller
   - 检测内存映射异常

9. **时间作弊检测** ✅
   - 检测系统时间跳跃
   - 检测启动时间异常
   - 支持服务器时间同步验证

10. **VPN 检测** ✅
    - 检测 VPN 连接状态

## 快速开始

### 1. 添加依赖

在项目的 `settings.gradle` 中添加：
```gradle
include ':AntiCheat'
```

在应用模块的 `build.gradle` 中添加：
```gradle
dependencies {
    implementation project(':AntiCheat')
}
```

### 2. 基础用法

```java
// 创建安全检测器
SecurityDetector securityDetector = new SecurityDetector(context, 
    new SecurityDetector.SecurityCallback() {
    
    @Override
    public void onThreatDetected(String threatType, String description, int riskLevel) {
        // 检测到威胁时的回调
        Log.w("Security", threatType + ": " + description);
    }
    
    @Override
    public void onDetectionComplete(boolean passed, List<DetectionResult> threats) {
        // 检测完成
        if (!passed) {
            // 处理安全威胁
            handleSecurityThreats(threats);
        }
    }
});

// 执行安全检测
securityDetector.performSecurityCheck();
```

### 3. 获取安全分数

```java
// 获取安全分数（0-100）
int score = securityDetector.getSecurityScore();

// 生成详细报告
String report = securityDetector.generateSecurityReport();
System.out.println(report);
```

## 高级用法

### 单独使用各检测器

```java
// 1. Root 检测
RootDetector rootDetector = new RootDetector(context);
if (rootDetector.isDeviceRooted()) {
    String details = rootDetector.getRootDetails();
    // 处理 Root 设备
}

// 2. 模拟器检测
EmulatorDetector emulatorDetector = new EmulatorDetector(context);
if (emulatorDetector.isEmulator()) {
    String details = emulatorDetector.getEmulatorDetails();
    // 处理模拟器
}

// 3. Hook 检测
HookDetector hookDetector = new HookDetector(context);
if (hookDetector.detectHookFramework()) {
    String details = hookDetector.getHookDetails();
    // 处理 Hook 框架
}

// 4. 签名验证
SignatureValidator validator = new SignatureValidator(context);
validator.setExpectedSignature("YOUR_SIGNATURE_SHA256");
if (!validator.verifySignature()) {
    // 签名被篡改
}

// 5. 时间作弊检测
TimeCheatDetector timeDetector = new TimeCheatDetector(context);
timeDetector.setServerTime(serverTimestamp);
if (timeDetector.detectTimeManipulation()) {
    // 时间被修改
}
```

### 自定义安全策略

```java
// 获取检测结果
Map<String, DetectionResult> results = securityDetector.getResults();

// 根据风险等级处理
for (DetectionResult result : results.values()) {
    if (result.detected) {
        switch (result.riskLevel) {
            case 5: // 严重威胁
                exitApp();
                break;
            case 4: // 高风险
                showWarning();
                break;
            case 3: // 中等风险
                logWarning();
                break;
            default: // 低风险
                // 仅记录
                break;
        }
    }
}
```

## 风险等级说明

| 等级 | 描述 | 建议处理 |
|-----|------|---------|
| 5 | 严重威胁 | 立即退出应用 |
| 4 | 高风险 | 警告并限制功能 |
| 3 | 中等风险 | 记录并警告 |
| 2 | 低风险 | 仅记录 |
| 1 | 极低风险 | 忽略或记录 |

## 检测项目详情

### Root 检测风险等级：5
- 检测到 Root 设备通常意味着系统完整性被破坏
- Root 用户可以修改应用数据、绕过安全机制

### 模拟器检测风险等级：3
- 模拟器常用于自动化作弊
- 但也有合法使用场景（开发测试）

### Hook 框架检测风险等级：5
- Xposed/Frida 可以完全控制应用行为
- 可以绕过几乎所有安全检测

### 调试器检测风险等级：4
- 调试器可以分析应用逻辑
- 可能用于逆向工程

### 签名验证风险等级：5
- 签名不匹配表示应用被重新打包
- 可能植入了恶意代码

### 多开检测风险等级：4
- 多开工具可能用于批量作弊
- 虚拟环境可以绕过设备限制

### 内存修改器检测风险等级：5
- 直接修改内存数据
- 可以任意修改游戏数值

### 时间作弊检测风险等级：3
- 修改系统时间绕过时间限制
- 可能影响计时相关功能

## 最佳实践

### 1. 应用启动时检测

```java
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 在应用启动时执行安全检测
        performSecurityCheck();
    }
    
    private void performSecurityCheck() {
        SecurityDetector detector = new SecurityDetector(this, callback);
        detector.performSecurityCheck();
    }
}
```

### 2. 定期检测

```java
// 使用定时器定期检测
Handler handler = new Handler();
Runnable securityCheckTask = new Runnable() {
    @Override
    public void run() {
        securityDetector.performSecurityCheck();
        handler.postDelayed(this, 60000); // 每分钟检测一次
    }
};
handler.post(securityCheckTask);
```

### 3. 关键操作前检测

```java
// 在关键操作前进行检测
public void performCriticalOperation() {
    if (securityDetector.getSecurityScore() < 60) {
        // 安全分数过低，拒绝操作
        showSecurityWarning();
        return;
    }
    
    // 执行关键操作
    doOperation();
}
```

### 4. 上报服务器

```java
@Override
public void onThreatDetected(String threatType, String description, int riskLevel) {
    // 将安全威胁上报到服务器
    reportToServer(threatType, description, riskLevel);
}

private void reportToServer(String type, String desc, int risk) {
    // 调用服务器API上报
    // API.reportSecurity(type, desc, risk);
}
```

## 注意事项

1. **性能影响**
   - 检测操作在后台线程执行，不会阻塞主线程
   - 完整检测耗时约 100-500ms

2. **误报处理**
   - 模拟器检测可能误报真实设备
   - 建议根据实际情况调整风险等级

3. **兼容性**
   - 支持 Android API 23+
   - 某些检测在不同设备上效果可能不同

4. **签名配置**
   - 发布前务必配置正确的签名SHA256
   - 可以通过 `getCurrentSignatureSHA256()` 获取

5. **加固配置**
   - 建议配合代码混淆使用
   - 可以使用加固服务进一步保护

## 获取应用签名

```bash
# 方法1: 使用 keytool
keytool -list -v -keystore your.keystore

# 方法2: 运行时获取
SignatureValidator validator = new SignatureValidator(context);
String signature = validator.getCurrentSignatureSHA256();
Log.i("Signature", signature);
```

## 常见问题

### Q: 如何降低误报率？
A: 可以调整风险等级阈值，或者将某些检测项设为仅记录而不拒绝。

### Q: 检测会影响性能吗？
A: 所有检测都在后台线程执行，对用户体验影响极小。

### Q: 可以绕过这些检测吗？
A: 任何客户端检测都可能被绕过，建议配合服务器端验证。

### Q: 需要特殊权限吗？
A: 大部分检测不需要额外权限，但代理检测需要网络状态权限。

## 许可证

MIT License

## 更新日志

### v1.0.0 (2025-01-01)
- ✅ 初始版本发布
- ✅ 支持10种常见安全检测
- ✅ 完整的回调机制
- ✅ 安全分数评估
- ✅ 详细的检测报告

## 技术支持

如有问题，请提交 Issue 或联系开发团队。


