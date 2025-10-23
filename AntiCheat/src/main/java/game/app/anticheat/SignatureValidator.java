package game.app.anticheat;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * 签名验证器
 * 验证应用签名是否被篡改
 */
public class SignatureValidator {
    
    private final Context context;
    private final List<String> detectedMethods = new ArrayList<>();
    
    // 正确的签名SHA256值（需要根据实际情况配置）
    // 留空表示不验证具体签名，只检查是否被二次打包
    private String expectedSignatureSHA256 = null;
    
    public SignatureValidator(Context context) {
        this.context = context.getApplicationContext();
    }
    
    /**
     * 设置期望的签名SHA256值
     */
    public void setExpectedSignature(String sha256) {
        this.expectedSignatureSHA256 = sha256;
    }
    
    /**
     * 验证签名
     */
    public boolean verifySignature() {
        detectedMethods.clear();
        
        try {
            PackageManager pm = context.getPackageManager();
            String packageName = context.getPackageName();
            PackageInfo packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
            
            Signature[] signatures = packageInfo.signatures;
            
            if (signatures == null || signatures.length == 0) {
                detectedMethods.add("无法获取签名信息");
                return false;
            }
            
            // 获取当前签名
            String currentSignature = getSignatureSHA256(signatures[0]);
            
            // 如果设置了期望签名，进行对比
            if (expectedSignatureSHA256 != null && !expectedSignatureSHA256.isEmpty()) {
                if (!expectedSignatureSHA256.equalsIgnoreCase(currentSignature)) {
                    detectedMethods.add("签名不匹配: " + currentSignature);
                    return false;
                }
            }
            
            // 检查签名证书信息
            if (!checkCertificateInfo(signatures[0])) {
                return false;
            }
            
            detectedMethods.add("签名验证通过");
            return true;
            
        } catch (Exception e) {
            detectedMethods.add("签名验证异常: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取签名的SHA256值
     */
    private String getSignatureSHA256(Signature signature) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(signature.toByteArray());
            byte[] digest = md.digest();
            
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02X", b));
            }
            return sb.toString();
            
        } catch (Exception e) {
            return "";
        }
    }
    
    /**
     * 检查证书信息
     */
    private boolean checkCertificateInfo(Signature signature) {
        try {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            ByteArrayInputStream stream = new ByteArrayInputStream(signature.toByteArray());
            X509Certificate cert = (X509Certificate) certFactory.generateCertificate(stream);
            
            // 检查证书有效期
            try {
                cert.checkValidity();
            } catch (Exception e) {
                detectedMethods.add("证书已过期或尚未生效");
                return false;
            }
            
            // 检查证书颁发者（Debug证书通常有特定的颁发者）
            String issuer = cert.getIssuerDN().getName();
            if (issuer.contains("Android Debug")) {
                detectedMethods.add("使用Debug证书");
                // 这里可以根据需求决定是否返回false
            }
            
            return true;
            
        } catch (Exception e) {
            detectedMethods.add("证书解析失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取当前应用签名SHA256
     */
    public String getCurrentSignatureSHA256() {
        try {
            PackageManager pm = context.getPackageManager();
            String packageName = context.getPackageName();
            PackageInfo packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
            
            if (packageInfo.signatures != null && packageInfo.signatures.length > 0) {
                return getSignatureSHA256(packageInfo.signatures[0]);
            }
        } catch (Exception ignored) {
        }
        return "";
    }
    
    /**
     * 获取签名详情
     */
    public String getSignatureDetails() {
        if (detectedMethods.isEmpty()) {
            return "未进行签名验证";
        }
        return String.join("; ", detectedMethods);
    }
}

