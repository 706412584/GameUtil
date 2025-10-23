package game.core;

/**
 * 日志接口
 * 开发者可以实现此接口来自定义日志输出
 */
public interface Logger {
    void d(String tag, String message);
    void i(String tag, String message);
    void w(String tag, String message);
    void e(String tag, String message);
    
    /**
     * 默认的日志实现（使用System.out）
     */
    class DefaultLogger implements Logger {
        @Override
        public void d(String tag, String message) {
            System.out.println("[D][" + tag + "] " + message);
        }
        
        @Override
        public void i(String tag, String message) {
            System.out.println("[I][" + tag + "] " + message);
        }
        
        @Override
        public void w(String tag, String message) {
            System.out.println("[W][" + tag + "] " + message);
        }
        
        @Override
        public void e(String tag, String message) {
            System.err.println("[E][" + tag + "] " + message);
        }
    }
}

