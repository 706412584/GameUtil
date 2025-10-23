package game.app.anticheat;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.ProxyInfo;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * 代理检测器
 * 检测网络代理设置
 */
public class ProxyDetector {
    
    private final Context context;
    private final List<String> detectedMethods = new ArrayList<>();
    
    public ProxyDetector(Context context) {
        this.context = context.getApplicationContext();
    }
    
    /**
     * 检测代理
     */
    public boolean isProxySet() {
        detectedMethods.clear();
        
        return checkSystemProxy() || checkHttpProxy() || checkVpnProxy();
    }
    
    /**
     * 检查系统代理
     */
    private boolean checkSystemProxy() {
        try {
            String proxyHost = System.getProperty("http.proxyHost");
            String proxyPort = System.getProperty("http.proxyPort");
            
            if (proxyHost != null && !proxyHost.isEmpty()) {
                detectedMethods.add("系统代理: " + proxyHost + ":" + proxyPort);
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }
    
    /**
     * 检查HTTP代理
     */
    private boolean checkHttpProxy() {
        try {
            ConnectivityManager cm = (ConnectivityManager) 
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
            
            if (cm != null) {
                NetworkInfo networkInfo = cm.getActiveNetworkInfo();
                if (networkInfo != null && networkInfo.isConnected()) {
                    // API 23+
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        ProxyInfo proxyInfo = cm.getDefaultProxy();
                        if (proxyInfo != null) {
                            String host = proxyInfo.getHost();
                            int port = proxyInfo.getPort();
                            if (host != null && !host.isEmpty()) {
                                detectedMethods.add("HTTP代理: " + host + ":" + port);
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }
    
    /**
     * 检查VPN代理
     */
    private boolean checkVpnProxy() {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
            
            if (cm != null) {
                NetworkInfo networkInfo = cm.getActiveNetworkInfo();
                if (networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_VPN) {
                    detectedMethods.add("检测到VPN连接");
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }
    
    /**
     * 获取代理详情
     */
    public String getProxyDetails() {
        if (detectedMethods.isEmpty()) {
            return "未检测到代理";
        }
        return String.join("; ", detectedMethods);
    }
}

