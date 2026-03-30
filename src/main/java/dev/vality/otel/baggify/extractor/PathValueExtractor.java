package dev.vality.otel.baggify.extractor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Extracts values from method invocation context using SpEL expressions.
 *
 * <p>Supports:
 * <ul>
 *   <li>Method argument variables ({@code #paramName}, {@code #p0})</li>
 *   <li>Nested property access and null-safe navigation</li>
 *   <li>Root object access (target bean)</li>
 * </ul>
 *
 * <p>This class is designed to be fault-tolerant. Invalid paths or
 * missing values result in null return values with warning logs,
 * not exceptions.
 */
@Slf4j
public class PathValueExtractor {
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final ConcurrentMap<String, Expression> expressionCache = new ConcurrentHashMap<>();

    /**
     * Extracts a value from method arguments/root object using a SpEL expression.
     *
     * @param path       SpEL expression
     * @param method     the method being invoked
     * @param args       method arguments
     * @param rootObject root object for non-# expressions (e.g. target bean)
     * @return extracted value or null when expression cannot be resolved
     */
    public Object extractValue(String path, Method method, Object[] args, Object rootObject) {
        try {
            Object[] invocationArgs = args != null ? args : new Object[0];

            var context = new MethodBasedEvaluationContext(rootObject, method, invocationArgs, parameterNameDiscoverer);
            var expression = expressionCache.computeIfAbsent(path, expressionParser::parseExpression);
            return expression.getValue(context);

        } catch (Exception e) {
            log.warn("Failed to extract value at path '{}' from method '{}': {}",
                    path, method.getName(), e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("Value extraction error details", e);
            }
            return null;
        }
    }
}
