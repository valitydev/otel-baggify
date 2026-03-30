package dev.vality.otel.baggify.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import dev.vality.otel.baggify.converter.BaggageValueConverter;

/**
 * Describes a single baggage field mapping from method argument to baggage key.
 *
 * <p>Example usage:
 * <pre>{@code
 * @WithBaggage({
 *     @BaggageField(key = "user.id", path = "#userId"),
 *     @BaggageField(key = "order.id", path = "#request.orderId", addToSpanAttributes = false)
 * })
 * public void processOrder(String userId, OrderRequest request) {
 *     // baggage is enriched during method execution
 *     // user.id is also added to span attributes (default)
 *     // order.id is only in baggage
 * }
 * }</pre>
 *
 * @see WithBaggage
 * @see BaggageValueConverter
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({}) // Only used as part of @WithBaggage
public @interface BaggageField {

    /**
     * The baggage key name.
     * <p>
     * Must be non-empty and unique within a single {@link WithBaggage} annotation.
     *
     * @return the baggage key
     */
    String key();

    /**
     * SpEL expression to extract a value.
     * <p>
     * Supports:
     * <ul>
     *   <li>Method arguments via variables ({@code #userId}, {@code #request.user.id}, {@code #p0})</li>
     *   <li>Root object access (target bean), e.g. {@code tenantId} or {@code getTenantId()}</li>
     *   <li>Regular SpEL operators and null-safe navigation ({@code ?.})</li>
     * </ul>
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code #userId} - extracts the userId parameter directly</li>
     *   <li>{@code #request.user.id} - navigates to request.getUser().getId()</li>
     *   <li>{@code #order.customer?.email} - null-safe nested field access</li>
     *   <li>{@code getDefaultTenantId()} - invoke method on target bean</li>
     * </ul>
     *
     * <p><b>Note:</b> Named argument access ({@code #paramName}) requires parameter names
     * to be available at runtime. Spring Boot Starter Parent enables this automatically.
     *
     * @return the extraction path
     */
    String path();

    /**
     * Whether to also write this field's value to the current span's attributes.
     * <p>
     * When {@code true} (default):
     * <ul>
     *   <li>Value is written to both Baggage and Span Attributes</li>
     *   <li>If no active span exists, only Baggage is enriched (no error)</li>
     * </ul>
     * <p>
     * When {@code false}:
     * <ul>
     *   <li>Value is written only to Baggage</li>
     * </ul>
     *
     * @return {@code true} to add value to span attributes (default)
     */
    boolean addToSpanAttributes() default true;

    /**
     * Optional custom converter class for value transformation.
     * <p>
     * When specified, this converter takes highest priority over all other
     * conversion mechanisms (including Spring's ConversionService).
     * <p>
     * The converter class must have a no-arg constructor.
     *
     * @return the converter class, or {@link BaggageValueConverter.NoOp} if not specified
     */
    Class<? extends BaggageValueConverter<?>> converter() default BaggageValueConverter.NoOp.class;

    /**
     * Optional Spring bean name of a custom converter.
     * <p>
     * When specified (non-empty), the bean is looked up from the Spring context
     * and used for value transformation. Takes highest priority when both
     * {@link #converter()} and {@link #converterBean()} are specified.
     *
     * @return the converter bean name, or empty string if not specified
     */
    String converterBean() default "";
}
