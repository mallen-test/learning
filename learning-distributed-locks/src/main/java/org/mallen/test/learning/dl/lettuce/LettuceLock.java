package org.mallen.test.learning.dl.lettuce;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;

/**
 * @author mallen
 * @date 2/17/20
 */
public class LettuceLock {
    private static RedisClient REDIS_CLIENT = null;
    private static StatefulRedisConnection<String, String> CONNECTION = null;
    /**
     * 过期时间：10s
     */
    private static int EXPIRE = 10;
    private static SetArgs setArgs = SetArgs.Builder.nx().ex(EXPIRE);
    private static String RELEASE_LUA =
            "if redis.call(\"get\",KEYS[1]) == ARGV[1] then\n" +
                    "    return redis.call(\"del\",KEYS[1])\n" +
                    "else\n" +
                    "    return 0\n" +
                    "end";

    public LettuceLock() {
        RedisURI redisURI = RedisURI.builder()
                .withHost("127.0.0.1")
                .withPort(6379)
                .withDatabase(1)
                .build();
        REDIS_CLIENT = RedisClient.create(redisURI);
        CONNECTION = REDIS_CLIENT.connect();
    }

    public Boolean tryLock(String key, String value) {
        Boolean result = false;
        try {
            String rc = CONNECTION.sync().set(key, value, setArgs);
            if ("OK".equals(rc)) {
                result = true;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return result;
    }

    public void releaseLock(String key, String value) {
        try {
            Long result = CONNECTION.sync().eval(RELEASE_LUA, ScriptOutputType.INTEGER, new String[]{key}, value);
            // 返回1表示执行del命令成功
            if (1 != result) {
                System.out.println("释放锁失败");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void destory() {
        if (null != CONNECTION) {
            CONNECTION.close();
        }
        if (null != REDIS_CLIENT) {
            REDIS_CLIENT.shutdown();
        }
    }
}
