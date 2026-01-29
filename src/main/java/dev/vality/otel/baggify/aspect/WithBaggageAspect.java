package dev.vality.otel.baggify.aspect;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import dev.vality.otel.baggify.annotation.BaggageField;
import dev.vality.otel.baggify.annotation.WithBaggage;
import dev.vality.otel.baggify.converter.BaggageValueConverterResolver;
import dev.vality.otel.baggify.extractor.PathValueExtractor;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

/**
 * Spring AOP aspect that handles {@link WithBaggage} annotation processing.
 *
 * <h2>Functionality</h2>
 * <ul>
 *   <li>Extracts values from method arguments using path expressions</li>
 *   <li>Enriches OpenTelemetry Baggage with extracted values</li>
 *   <li>Optionally writes values to current Span Attributes</li>
 *   <li>Restores original context after method execution</li>
 * </ul>
 *
 * <h2>Error Handling</h2>
 * <p>This aspect is designed to never break business logic. All errors during
 * baggage enrichment are logged as warnings and silently ignored. The original
 * method will always be executed regardless of any issues with baggage processing.
 *
 * <h2>Aspect Ordering</h2>
 * <p>This aspect has {@code Ordered.LOWEST_PRECEDENCE - 100} priority to ensure
 * it executes <b>after</b> the OpenTelemetry {@code @WithSpan} aspect.
 *
 * @see WithBaggage
 * @see BaggageField
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class WithBaggageAspect implements Ordered {

    /**
     * Order value for this aspect.
     */
    public static final int ASPECT_ORDER = Ordered.LOWEST_PRECEDENCE - 100;

    private final PathValueExtractor pathValueExtractor;
    private final BaggageValueConverterResolver converterResolver;

    @Override
    public int getOrder() {
        return ASPECT_ORDER;
    }

    /**
     * Around advice that processes {@link WithBaggage} annotated methods.
     * <p>
     * This method never throws exceptions related to baggage processing.
     * All errors are logged and the original method is always executed.
     *
     * @param joinPoint   the join point representing the intercepted method
     * @param withBaggage the WithBaggage annotation
     * @return the result of the method invocation
     * @throws Throwable if the method throws an exception
     */
    @Around("@annotation(withBaggage)")
    public Object aroundWithBaggage(ProceedingJoinPoint joinPoint,
                                    WithBaggage withBaggage) throws Throwable {
        Context enrichedContext = null;

        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            Object[] args = joinPoint.getArgs();

            // Validate configuration and log warnings (don't fail)
            if (!validateConfiguration(withBaggage, method)) {
                return joinPoint.proceed();
            }

            // Build enriched baggage
            BaggageBuilder baggageBuilder = Baggage.current().toBuilder();
            Span currentSpan = Span.current();
            boolean hasEnrichment = false;

            for (BaggageField field : withBaggage.value()) {
                if (processField(field, method, args, baggageBuilder, currentSpan)) {
                    hasEnrichment = true;
                }
            }

            // Only create new context if we actually enriched something
            if (hasEnrichment) {
                Context currentContext = Context.current();
                Baggage enrichedBaggage = baggageBuilder.build();
                enrichedContext = currentContext.with(enrichedBaggage);
            }
        } catch (Exception e) {
            log.warn("Failed to enrich baggage for method '{}': {}",
                    joinPoint.getSignature().getName(), e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("Baggage enrichment error details", e);
            }
        }

        // Execute method with enriched context (or original if enrichment failed)
        if (enrichedContext != null) {
            try (Scope ignored = enrichedContext.makeCurrent()) {
                return joinPoint.proceed();
            }
        } else {
            return joinPoint.proceed();
        }
    }

    /**
     * Validates the @WithBaggage configuration.
     * Logs warnings for any issues but doesn't throw exceptions.
     *
     * @param withBaggage the annotation to validate
     * @param method      the annotated method
     * @return true if configuration is valid and processing should continue
     */
    private boolean validateConfiguration(WithBaggage withBaggage, Method method) {
        BaggageField[] fields = withBaggage.value();
        String methodName = method.getName();

        if (fields == null || fields.length == 0) {
            log.warn("Method '{}': @WithBaggage has no @BaggageField definitions, skipping",
                    methodName);
            return false;
        }

        Set<String> seenKeys = new HashSet<>();
        boolean hasValidFields = false;

        for (BaggageField field : fields) {
            String key = field.key();
            String path = field.path();

            // Validate key
            if (key == null || key.isBlank()) {
                log.warn("Method '{}': @BaggageField has empty key, skipping this field",
                        methodName);
                continue;
            }

            // Check uniqueness
            if (!seenKeys.add(key)) {
                log.warn("Method '{}': Duplicate baggage key '{}', " +
                        "only first occurrence will be used", methodName, key);
                continue;
            }

            // Validate path
            if (path == null || path.isBlank()) {
                log.warn("Method '{}': @BaggageField with key '{}' has empty path, skipping",
                        methodName, key);
                continue;
            }

            if (!path.startsWith("#")) {
                log.warn("Method '{}': @BaggageField path '{}' for key '{}' must start with '#', skipping",
                        methodName, path, key);
                continue;
            }

            hasValidFields = true;
        }

        return hasValidFields;
    }

    /**
     * Processes a single baggage field: extracts value, converts it,
     * and adds to baggage/span.
     * <p>
     * Never throws exceptions - all errors are logged as warnings.
     *
     * @param field          the baggage field definition
     * @param method         the method being invoked
     * @param args           the method arguments
     * @param baggageBuilder the baggage builder to add value to
     * @param currentSpan    the current span
     * @return true if value was successfully added to baggage
     */
    private boolean processField(BaggageField field, Method method, Object[] args,
                                 BaggageBuilder baggageBuilder, Span currentSpan) {
        String key = field.key();
        String path = field.path();

        try {
            // Extract value using path
            Object value = pathValueExtractor.extractValue(path, method, args);

            // Skip null values silently
            if (value == null) {
                log.trace("Method '{}': Value at path '{}' is null, skipping key '{}'",
                        method.getName(), path, key);
                return false;
            }

            // Convert to string
            String stringValue = converterResolver.convert(value, field);

            // Skip null converted values
            if (stringValue == null) {
                log.trace("Method '{}': Converted value for key '{}' is null, skipping",
                        method.getName(), key);
                return false;
            }

            // Add to baggage
            baggageBuilder.put(key, stringValue);

            // Add to span attributes if enabled for this field (default: true)
            if (field.addToSpanAttributes() && currentSpan.isRecording()) {
                currentSpan.setAttribute(key, stringValue);
            }

            log.trace("Method '{}': Added baggage key '{}' with value '{}'",
                    method.getName(), key, stringValue);
            return true;

        } catch (Exception e) {
            log.warn("Method '{}': Failed to process @BaggageField with key '{}' and path '{}': {}",
                    method.getName(), key, path, e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("Baggage field processing error details", e);
            }
            return false;
        }
    }
}
