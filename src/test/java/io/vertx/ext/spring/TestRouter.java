package io.vertx.ext.spring;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.spring.annotation.RouterParam;
import io.vertx.ext.spring.annotation.RouterPathVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.HashMap;
import java.util.Optional;

import io.vertx.core.http.HttpMethod;
import io.vertx.ext.spring.annotation.RouterHandler;
import io.vertx.ext.spring.annotation.VertxRouter;
import io.vertx.ext.web.RoutingContext;
import io.vertx.redis.RedisClient;

@VertxRouter
public class TestRouter {

    @Autowired
    @Qualifier("testRedis")
    RedisClient redis;

    @Autowired InMemoryCache testCache;

    @RouterHandler(method = HttpMethod.GET, value = "/test")
    public void testGet(RoutingContext context, String key) {
        context.response()
            .end(testCache.get(key));
    }

    @RouterHandler(method = HttpMethod.PUT, value = "/test")
    public void testSet(RoutingContext context, @RouterParam("key") String keyParam,
                        @RouterParam Integer value, @RouterParam(required = false) String parameterThatIsNotThere) {

        testCache.set(keyParam, value.toString());

        context.response().end(value.toString());
    }

    @RouterHandler(method = HttpMethod.GET, value = "/redis")
    public void testRedisGet(RoutingContext context, HttpServerRequest request) {
        String key = request.params().get("key");
        redis.get(key, (res) -> {
            if (res.failed()) {
                context.response()
                    .setStatusCode(500)
                    .end(res.cause().getMessage());
                return;
            }
            context.response()
                .end(Optional.ofNullable(res.result()).orElse(""));
        });
    }

    @RouterHandler(method = HttpMethod.PUT, value = "/redis")
    public void testRedisSet(RoutingContext context, @RouterParam Long value) {
        String key = context.request().params().get("key");
        redis.set(key, value.toString(), (res) -> {
            if (res.failed()) {
                context.response()
                    .setStatusCode(500)
                    .end(res.cause().getMessage());
                return;
            }
            context.response().end(value.toString());
        });
    }

    @RouterHandler(method = HttpMethod.GET, value = "/test/:key")
    public void testPathGet(RoutingContext context, @RouterPathVariable String key) {
        context.response()
                .end(testCache.get(key));
    }
}
