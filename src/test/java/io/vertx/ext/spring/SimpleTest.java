package io.vertx.ext.spring;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.test.core.AsyncTestBase;

import java.util.ArrayList;
import java.util.Map;

public class SimpleTest extends AsyncTestBase {

    private Vertx vertx = Vertx.vertx();

    private HttpClient client;

    @Test
    public void testServer() {
        vertx = Vertx.vertx();
        client = vertx.createHttpClient();

        VertxSpring.deploy(vertx,
                "test-context.xml",
                new HttpServerOptions()
                        .setPort(8080),
                (deployRes) -> {
                    if (deployRes.failed()) {
                        deployRes.cause().printStackTrace();
                        assertTrue(deployRes.succeeded());
                        return;
                    }
                    try {
                        testAll((testRes) ->
                                vertx.undeploy(deployRes.result(), (undeployRes) -> {
                                    testComplete();
                                })
                        );
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();
                    }
                });
        await();
    }

    private void testAll(Handler<AsyncResult<Void>> handler) throws JsonProcessingException {
        TestJsonObject obj = new TestJsonObject();
        obj.setId(2L);
        obj.setName("name");

        ArrayList<TestAnimal> objects = new ArrayList<>();
        objects.add(new CatAnimal());
        objects.add(new DogAnimal());

        obj.setAnimals(objects);

        Json.mapper.enableDefaultTypingAsProperty(ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.Id.MINIMAL_CLASS.getDefaultPropertyName());
        Buffer json = Json.encodeToBuffer(obj);

        testPostJson(json, (h) -> {

            testPut("a", "1", (ar) ->
                    testGet("a", "1", (a) -> {

                        testPathGet("a", "1", handler);
                    })
            );
        });
    }

    private void testPostJson(Buffer json, Handler<AsyncResult<Void>> handler) {
        client.post(8080, "127.0.0.1", "/testJson", (response) -> {
            if (response.statusCode() != 200) {
                handler.handle(Future.failedFuture(response.statusMessage()));
                return;
            }
            response
                    .exceptionHandler((e) ->
                            handler.handle(Future.failedFuture(e))
                    )
                    .bodyHandler((buff) -> {
                        TestAnimal animal = Json.decodeValue(buff, TestAnimal.class);

                        assertEquals("woof!", animal.speak());
                        handler.handle(Future.succeededFuture());
                    });
        }).end(json);
    }

    private void testPut(String key, String value, Handler<AsyncResult<Void>> handler) {
        client.put(8080, "127.0.0.1", "/test?key=" + key + "&value=" + value, (response) -> {
            if (response.statusCode() != 200) {
                handler.handle(Future.failedFuture(response.statusMessage()));
                return;
            }
            response
                    .exceptionHandler((e) ->
                            handler.handle(Future.failedFuture(e))
                    )
                    .bodyHandler((buff) ->
                            handler.handle(Future.succeededFuture())
                    );
        }).end();
    }

    private void testGet(String key, String value, Handler<AsyncResult<Void>> handler) {
        client.getNow(8080, "127.0.0.1", "/test?key=" + key, (response) -> {
            if (response.statusCode() != 200) {
                handler.handle(Future.failedFuture(response.statusMessage()));
                return;
            }
            response
                    .exceptionHandler((e) ->
                            handler.handle(Future.failedFuture(e))
                    )
                    .bodyHandler((buff) -> {
                        assertEquals(value, buff.toString());
                        handler.handle(Future.succeededFuture());
                    });
        });
    }

    private void testPathGet(String key, String value, Handler<AsyncResult<Void>> handler) {
        client.getNow(8080, "127.0.0.1", "/test/" + key, (response) -> {
            if (response.statusCode() != 200) {
                handler.handle(Future.failedFuture(response.statusMessage()));
                return;
            }
            response
                    .exceptionHandler((e) ->
                            handler.handle(Future.failedFuture(e))
                    )
                    .bodyHandler((buff) -> {
                        assertEquals(value, buff.toString());
                        handler.handle(Future.succeededFuture());
                    });
        });
    }

}
