package dev.vality.otel.baggify.converter;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.core.convert.ConversionService;
import org.springframework.lang.Nullable;

import dev.vality.otel.baggify.annotation.BaggageField;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves the appropriate converter for baggage value conversion.
 *
 * <p>Conversion priority (R18):
 * <ol>
 *   <li>Custom converter specified via {@link BaggageField#converterBean()}</li>
 *   <li>Custom converter specified via {@link BaggageField#converter()}</li>
 *   <li>Spring {@link ConversionService} (if available and can convert)</li>
 *   <li>Direct String (no-op)</li>
 *   <li>{@link Object#toString()}</li>
 * </ol>
 *
 * <p>This class is designed to be fault-tolerant. Conversion failures
 * result in null return values with warning logs, not exceptions.
 */
@Slf4j
public class BaggageValueConverterResolver {

    private final BeanFactory beanFactory;
    private final ConversionService conversionService;
    private final ConcurrentMap<Class<?>, BaggageValueConverter<?>> converterCache =
            new ConcurrentHashMap<>();

    public BaggageValueConverterResolver(BeanFactory beanFactory,
                                         @Nullable ConversionService conversionService) {
        this.beanFactory = beanFactory;
        this.conversionService = conversionService;
    }

    /**
     * Converts the given value to String using the appropriate converter.
     * <p>
     * This method never throws exceptions. Conversion failures result in
     * null return values with warning logs.
     *
     * @param value        the value to convert (may be null)
     * @param baggageField the baggage field annotation with converter configuration
     * @return the string representation, or null if value should be skipped
     */
    @SuppressWarnings("unchecked")
    public String convert(@Nullable Object value, BaggageField baggageField) {
        if (value == null) {
            return null;
        }

        String key = baggageField.key();

        try {
            // 1. Try converter bean first (highest priority when specified)
            String converterBeanName = baggageField.converterBean();
            if (converterBeanName != null && !converterBeanName.isBlank()) {
                return convertWithBean(value, converterBeanName, key);
            }

            // 2. Try converter class
            Class<? extends BaggageValueConverter<?>> converterClass = baggageField.converter();
            if (converterClass != BaggageValueConverter.NoOp.class) {
                return convertWithClass(value, converterClass, key);
            }

            // 3. Try Spring ConversionService
            if (conversionService != null) {
                try {
                    if (conversionService.canConvert(value.getClass(), String.class)) {
                        return conversionService.convert(value, String.class);
                    }
                } catch (Exception e) {
                    log.trace("ConversionService failed for key '{}': {}", key, e.getMessage());
                    // Fall through to next option
                }
            }

            // 4. Direct String (no-op)
            if (value instanceof String stringValue) {
                return stringValue;
            }

            // 5. Fallback to toString()
            return value.toString();

        } catch (Exception e) {
            log.warn("Failed to convert value for baggage key '{}': {}", key, e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("Conversion error details for key '{}'", key, e);
            }
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String convertWithBean(Object value, String beanName, String key) {
        try {
            BaggageValueConverter<Object> converter =
                    (BaggageValueConverter<Object>) beanFactory.getBean(
                            beanName, BaggageValueConverter.class);
            return converter.convert(value);
        } catch (NoSuchBeanDefinitionException e) {
            log.warn("Converter bean '{}' not found for baggage key '{}', " +
                    "falling back to default conversion", beanName, key);
            return convertWithFallback(value);
        } catch (Exception e) {
            log.warn("Converter bean '{}' failed for baggage key '{}': {}, " +
                    "falling back to default conversion", beanName, key, e.getMessage());
            return convertWithFallback(value);
        }
    }

    @SuppressWarnings("unchecked")
    private String convertWithClass(Object value,
                                    Class<? extends BaggageValueConverter<?>> converterClass,
                                    String key) {
        try {
            BaggageValueConverter<Object> converter =
                    (BaggageValueConverter<Object>) getOrCreateConverter(converterClass);
            return converter.convert(value);
        } catch (Exception e) {
            log.warn("Converter class '{}' failed for baggage key '{}': {}, " +
                            "falling back to default conversion",
                    converterClass.getSimpleName(), key, e.getMessage());
            return convertWithFallback(value);
        }
    }

    private String convertWithFallback(Object value) {
        if (value instanceof String stringValue) {
            return stringValue;
        }
        return value.toString();
    }

    private BaggageValueConverter<?> getOrCreateConverter(
            Class<? extends BaggageValueConverter<?>> converterClass) {
        return converterCache.computeIfAbsent(converterClass, clazz -> {
            try {
                return converterClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Failed to instantiate converter: " + converterClass.getName() +
                                ". Ensure it has a public no-arg constructor.", e);
            }
        });
    }
}
