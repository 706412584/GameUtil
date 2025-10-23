package game.core.util;

import android.util.Base64;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 数据压缩工具类
 * 用于压缩大型存档数据，节省存储空间和提升传输效率
 * 
 * 支持GZIP压缩算法
 * 兼容 Android API 23+
 */
public class CompressionUtil {
    
    /**
     * 压缩字符串
     * @param str 原始字符串
     * @return Base64编码的压缩数据
     */
    public static String compress(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(str.getBytes("UTF-8"));
            gzip.finish();
            
            byte[] compressed = out.toByteArray();
            return Base64.encodeToString(compressed, Base64.NO_WRAP);
            
        } catch (IOException e) {
            e.printStackTrace();
            return str;
        }
    }
    
    /**
     * 解压缩字符串
     * @param compressedStr Base64编码的压缩数据
     * @return 解压后的字符串
     */
    public static String decompress(String compressedStr) {
        if (compressedStr == null || compressedStr.isEmpty()) {
            return compressedStr;
        }
        
        try {
            byte[] compressed = Base64.decode(compressedStr, Base64.NO_WRAP);
            
            try (ByteArrayInputStream in = new ByteArrayInputStream(compressed);
                 GZIPInputStream gzip = new GZIPInputStream(in);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                
                byte[] buffer = new byte[1024];
                int len;
                while ((len = gzip.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
                
                return out.toString("UTF-8");
            }
            
        } catch (IOException e) {
            e.printStackTrace();
            return compressedStr;
        }
    }
    
    /**
     * 压缩字节数组
     */
    public static byte[] compressBytes(byte[] data) {
        if (data == null || data.length == 0) {
            return data;
        }
        
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(data);
            gzip.finish();
            
            return out.toByteArray();
            
        } catch (IOException e) {
            e.printStackTrace();
            return data;
        }
    }
    
    /**
     * 解压缩字节数组
     */
    public static byte[] decompressBytes(byte[] compressed) {
        if (compressed == null || compressed.length == 0) {
            return compressed;
        }
        
        try (ByteArrayInputStream in = new ByteArrayInputStream(compressed);
             GZIPInputStream gzip = new GZIPInputStream(in);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzip.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            
            return out.toByteArray();
            
        } catch (IOException e) {
            e.printStackTrace();
            return compressed;
        }
    }
    
    /**
     * 计算压缩率
     * @param originalSize 原始大小
     * @param compressedSize 压缩后大小
     * @return 压缩率（百分比）
     */
    public static float calculateCompressionRatio(long originalSize, long compressedSize) {
        if (originalSize == 0) {
            return 0.0f;
        }
        return (1.0f - (float) compressedSize / originalSize) * 100;
    }
    
    /**
     * 判断是否应该压缩（小于阈值不压缩）
     * @param dataSize 数据大小（字节）
     * @param threshold 阈值（字节）
     * @return true表示应该压缩
     */
    public static boolean shouldCompress(int dataSize, int threshold) {
        return dataSize >= threshold;
    }
}

