package org.mallen.test.learning.dl.redis.lettuce.advance;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.RedisPubSubListener;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;

import java.util.concurrent.CountDownLatch;

/**
 * @author mallen
 * @date 2/17/20
 */
public class LettuceAdvanceLock {
    private static RedisClient REDIS_CLIENT = null;
    private static StatefulRedisConnection<String, String> CONNECTION = null;
    private static ThreadLocal<StatefulRedisPubSubConnection<String, String>> PSUB_CONNECTION = new ThreadLocal<>();
    private static String CHANNEL_NAME = "LettuceAdvanceLock";
    /**
     * 过期时间：10s
     */
    private static int EXPIRE = 10;
    private static SetArgs LOCK_ARGS = SetArgs.Builder.nx().ex(EXPIRE);
    private static SetArgs REEXPIRE_ARGS = SetArgs.Builder.ex(EXPIRE);
    private static String RELEASE_LUA =
            "if redis.call(\"get\",KEYS[1]) == ARGV[1] then\n" +
                    "    return redis.call(\"del\",KEYS[1])\n" +
                    "else\n" +
                    "    return 0\n" +
                    "end";

    public LettuceAdvanceLock() {
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
            String rc = CONNECTION.sync().set(key, value, LOCK_ARGS);
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

    /**
     * 为锁续时
     *
     * @param key
     * @param value
     */
    public Boolean reExpire(String key, String value) {
        Boolean result = false;
        try {
            String rc = CONNECTION.sync().set(key, value, REEXPIRE_ARGS);
            if ("OK".equals(rc)) {
                result = true;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return result;
    }

    public void pubReleaseMsg(String uuid) {
        // 发布/订阅消息时，每个线程需要单独拥有一个连接，所以需要使用线程本地变量来保存连接
        if (null == PSUB_CONNECTION.get()) {
            synchronized (LettuceAdvanceLock.class) {
                if (null == PSUB_CONNECTION.get()) {
                    PSUB_CONNECTION.set(REDIS_CLIENT.connectPubSub());
                }
            }
        }
        try {
            RedisPubSubCommands sync = PSUB_CONNECTION.get().sync();
            sync.publish(CHANNEL_NAME, uuid);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void subReleaseMsg(CountDownLatch notifier) {
        // 发布/订阅消息时，每个线程需要单独拥有一个连接，所以需要使用线程本地变量来保存连接
        if (null == PSUB_CONNECTION.get()) {
            synchronized (LettuceAdvanceLock.class) {
                if (null == PSUB_CONNECTION.get()) {
                    PSUB_CONNECTION.set(REDIS_CLIENT.connectPubSub());
                }
            }
        }
        RedisPubSubCommands<String, String> sync = PSUB_CONNECTION.get().sync();
        sync.getStatefulConnection().addListener(new RedisPubSubAdapter() {
            @Override
            public void message(Object channel, Object message) {
                // 接收到消息，通知业务线程，开始竞争锁
                notifier.countDown();
            }
        });
        sync.subscribe(CHANNEL_NAME);
    }

    public void unsubReleaseMsg() {
        // 发布/订阅消息时，每个线程需要单独拥有一个连接，所以需要使用线程本地变量来保存连接
        if (null == PSUB_CONNECTION.get()) {
            synchronized (LettuceAdvanceLock.class) {
                if (null == PSUB_CONNECTION.get()) {
                    PSUB_CONNECTION.set(REDIS_CLIENT.connectPubSub());
                }
            }
        }
        RedisPubSubCommands<String, String> sync = PSUB_CONNECTION.get().sync();
        sync.unsubscribe(CHANNEL_NAME);
    }

    public void destoryPsub() {
        if (null != PSUB_CONNECTION.get()) {
            PSUB_CONNECTION.get().close();
        }
    }
}
