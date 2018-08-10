package io.vertx.ext.spring.impl.factory;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.auth.User;
import io.vertx.ext.spring.annotation.*;
import io.vertx.ext.web.LanguageHeader;
import io.vertx.ext.web.Session;
import org.springframework.beans.BeansException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.convert.ConversionService;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

public class RouterFactory implements ApplicationContextAware, FactoryBean<Router> {

    private static Logger logger = LoggerFactory.getLogger(RouterFactory.class);

    @Autowired
    Vertx vertx;

    private ApplicationContext applicationContext;

    @Override
    public Router getObject() throws Exception {
        Router router = Router.router(vertx);
        Map<String, Object> handlers = applicationContext.getBeansWithAnnotation(
                VertxRouter.class);
        handlers.values().forEach((handler) -> registerHandlers(router, handler));
        return router;
    }

    @Override
    public Class<?> getObjectType() {
        return Router.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    private static void registerHandlers(Router router, Object handler) {
        VertxRouter controller = handler.getClass().getAnnotation(VertxRouter.class);
        for (Method method : handler.getClass().getMethods()) {
            RouterHandler mapping = method.getAnnotation(RouterHandler.class);
            if (mapping != null) {
                registerMethod(router, handler, method, controller, mapping);
            }
        }
    }

    private static void registerMethod(Router router,
                                       Object handler, Method method,
                                       VertxRouter controller, RouterHandler mapping) {
        if (mapping.method() == HttpMethod.POST || mapping.method() == HttpMethod.PUT) {
            router.route(mapping.method(), controller.value() + mapping.value())
                    .handler(BodyHandler.create());
        }
        if (mapping.worker()) {
            router.route(mapping.method(), controller.value() + mapping.value())
                    .blockingHandler(new RouterHandlerImpl(handler, method, mapping.method(), mapping.value()))
                    .failureHandler((ctx) -> {
                        logger.error(ctx.failure());
                        ctx.response()
                                .setStatusCode(500)
                                .setStatusMessage(ctx.failure().getMessage());
                    });
        } else {
            router.route(mapping.method(), controller.value() + mapping.value())
                    .handler(new RouterHandlerImpl(handler, method, mapping.method(), mapping.value()))
                    .failureHandler((ctx) -> {
                        logger.error(ctx.failure());
                        ctx.response()
                                .setStatusCode(500)
                                .setStatusMessage(ctx.failure().getMessage());
                    });
        }
        logger.info("Register handler " +
                mapping.method() + " " +
                controller.value() + mapping.value() +
                " on " +
                handler.getClass().getSimpleName() +
                "." +
                method.getName());
    }

    private static class RouterHandlerImpl implements Handler<RoutingContext> {
        static ConversionService conversionService = new DefaultFormattingConversionService();

        Object handler;
        Method method;
        HttpMethod httpMethod;
        String routerUrl;
        Map<String, String> uriTemplateVariables;

        RouterHandlerImpl(Object handler, Method method, HttpMethod httpMethod, String routerUrl) {
            this.handler = handler;
            this.method = method;
            this.httpMethod = httpMethod;
            this.routerUrl = routerUrl;
        }

        @Override
        public void handle(RoutingContext context) {
            logger.debug(context.request().method().toString() + " " + context.request().uri());

            try {
                // get all the required properties by the method
                if(method.getParameterCount() > 0) {
                    int argIndex = 0;
                    Object[] args = new Object[method.getParameterCount()];

                    DefaultParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();
                    String[] paramNames = discoverer.getParameterNames(method);

                    Parameter[] parameters = method.getParameters();
                    for (Parameter parameter : parameters) {
                        Object arg = null;

                        if(parameter.getType().isAssignableFrom(Locale.class)) {
                            arg = context.preferredLanguage();

                        } else if(parameter.getType().isAssignableFrom(User.class)) {
                            arg = context.user();

                        } else if(parameter.getType().isAssignableFrom(Session.class)) {
                            arg = context.session();

                        } else if(parameter.getType().isAssignableFrom(RoutingContext.class)) {
                            arg = context;

                        } else if(parameter.getType().isAssignableFrom(HttpServerRequest.class)) {
                            arg = context.request();

                        } else if(parameter.getType().isAssignableFrom(HttpServerResponse.class)) {
                            arg = context.response();

                        } else {
                            RouterParam routerParam = parameter.getAnnotation(RouterParam.class);
                            RouterPathVariable routerPathVariable = parameter.getAnnotation(RouterPathVariable.class);
                            RouterHeader routerHeader = parameter.getAnnotation(RouterHeader.class);

                            String paramName = paramNames[argIndex];
                            String paramValue = null;

                            if(routerHeader != null) {
                                if(!routerHeader.name().isEmpty() || !routerHeader.value().isEmpty()) {
                                    if(!routerHeader.name().isEmpty()) {
                                        paramName = routerHeader.name();

                                    } else if(!routerHeader.value().isEmpty()) {
                                        paramName = routerHeader.value();
                                    }
                                }

                                paramValue = context.request().getHeader(paramName);
                                if(paramValue == null && !routerHeader.defaultValue().isEmpty()) {
                                    paramValue = routerHeader.defaultValue();
                                }

                                if(paramValue == null && routerHeader.required()) {

                                    throwMissingRequiredParameterException(paramName);
                                } else {
                                    arg = convertParamValue(paramValue, parameter.getType());
                                }

                            } else if(routerPathVariable != null) {
                                if(!routerPathVariable.name().isEmpty() || !routerPathVariable.value().isEmpty()) {
                                    if(!routerPathVariable.name().isEmpty()) {
                                        paramName = routerPathVariable.name();

                                    } else if(!routerPathVariable.value().isEmpty()) {
                                        paramName = routerPathVariable.value();
                                    }
                                }

                                paramValue = context.pathParam(paramName);
                                if(paramValue == null && routerPathVariable.required()) {

                                    throwMissingRequiredParameterException(paramName);
                                } else {
                                    arg = convertParamValue(paramValue, parameter.getType());
                                }

                            } else {
                                if(routerParam != null && (!routerParam.name().isEmpty() || !routerParam.value().isEmpty())) {
                                    if(!routerParam.name().isEmpty()) {
                                        paramName = routerParam.name();

                                    } else if(!routerParam.value().isEmpty()) {
                                        paramName = routerParam.value();
                                    }
                                }

                                paramValue = context.request().params().get(paramName);
                                if(paramValue == null && (routerParam == null || routerParam.required())) {

                                    throwMissingRequiredParameterException(paramName);
                                } else {
                                    arg = convertParamValue(paramValue, parameter.getType());
                                }
                            }
                        }
                        args[argIndex++] = arg;
                    }

                    method.invoke(handler, args);
                } else {
                    method.invoke(handler);
                }

            } catch (Throwable e) {
                context.fail(e);
            }
        }

        private void throwMissingRequiredParameterException(String paramName) {
            // this is a parameter that was supposed to be sent - but it's not - throw an exception
            throw new UnsupportedOperationException(
                    "Handler method " +
                            handler.getClass().getSimpleName() + "." + method.getName() +
                            " parameter \"" + paramName + "\" is required and was not found in request parameters.");
        }

        private <T> T convertParamValue(String paramValue, Class<T> paramType) {
            if(conversionService.canConvert(paramType, String.class)) {
                return conversionService.convert(paramValue, paramType);
            } else {
                throw new TypeMismatchException(paramValue, paramType);
            }
        }
    }

}
