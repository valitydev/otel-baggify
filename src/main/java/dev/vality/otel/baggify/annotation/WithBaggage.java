package dev.vality.otel.baggify.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declarative annotation for enriching OpenTelemetry Baggage during method execution.
 *
 * <p>This annotation provides a Spring AOP mechanism to extract values from method arguments
 * and write them to OpenTelemetry Baggage. The baggage enrichment is scoped to the method
 * execution time - after the method completes (success or exception), the original OTEL
 * context is restored.
 *
 * <h2>Basic Usage</h2>
 * <pre>{@code
 * @WithBaggage(@BaggageField(key = "user.id", path = "#userId"))
 * public void processUser(String userId) {
 *     // Baggage AND Span Attributes contain "user.id" (addToSpanAttributes=true by default)
 * }
 * }</pre>
 *
 * <h2>Multiple Fields with Different Settings</h2>
 * <pre>{@code
 * @WithBaggage({
 *     @BaggageField(key = "user.id", path = "#request.userId"),
 *     @BaggageField(key = "order.id", path = "#request.orderId"),
 *     @BaggageField(key = "internal.trace", path = "#traceId", addToSpanAttributes = false)
 * })
 * public void processOrder(OrderRequest request, String traceId) {
 *     // user.id and order.id -> Baggage + Span Attributes
 *     // internal.trace -> Baggage only
 * }
 * }</pre>
 *
 * <h2>Aspect Ordering</h2>
 * <p>When used together with {@code @WithSpan}, the {@code @WithBaggage} aspect executes
 * <b>after</b> {@code @WithSpan}, meaning baggage enrichment happens inside the active span.
 * This is achieved through Spring AOP ordering (this aspect has lower priority).
 *
 * <h2>Requirements</h2>
 * <ul>
 *   <li>Method parameter names must be available at runtime (compile with {@code -parameters})</li>
 *   <li>Each {@code key} within a single annotation must be unique</li>
 *   <li>Each {@code path} must be a valid SpEL expression</li>
 * </ul>
 *
 * @see BaggageField
 * @see io.opentelemetry.api.baggage.Baggage
 * @see io.opentelemetry.instrumentation.annotations.WithSpan
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface WithBaggage {

    /**
     * Array of baggage field mappings.
     * <p>
     * Each field defines a key-path pair for baggage enrichment.
     * Keys must be unique within this annotation.
     * <p>
     * By default, each field is also added to span attributes
     * (configurable per field via {@link BaggageField#addToSpanAttributes()}).
     *
     * @return array of baggage field definitions
     */
    BaggageField[] value();
}
