package dev.vality.otel.baggify.config;

import dev.vality.otel.baggify.aspect.WithBaggageAspect;
import dev.vality.otel.baggify.converter.BaggageValueConverterResolver;
import dev.vality.otel.baggify.extractor.PathValueExtractor;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.convert.ConversionService;
import org.springframework.lang.Nullable;

/**
 * Spring Boot Auto Configuration for otel-baggify library.
 *
 * <p>This configuration automatically sets up the necessary beans for
 * {@link dev.vality.otel.baggify.annotation.WithBaggage} annotation processing:
 * <ul>
 *   <li>{@link PathValueExtractor} - for extracting values from method arguments</li>
 *   <li>{@link BaggageValueConverterResolver} - for value conversion</li>
 *   <li>{@link WithBaggageAspect} - the AOP aspect that processes annotations</li>
 * </ul>
 *
 * <h2>Conditional Activation</h2>
 * <p>This configuration is only activated when OpenTelemetry API classes are present
 * on the classpath.
 *
 * <h2>Customization</h2>
 * <p>Each bean is created with {@link ConditionalOnMissingBean}, allowing users to
 * provide their own implementations if needed.
 */
@AutoConfiguration
@EnableAspectJAutoProxy
@ConditionalOnClass(name = "io.opentelemetry.api.baggage.Baggage")
public class BaggifyAutoConfiguration {

    /**
     * Creates the {@link PathValueExtractor} bean.
     *
     * @return the path value extractor
     */
    @Bean
    @ConditionalOnMissingBean
    public PathValueExtractor pathValueExtractor() {
        return new PathValueExtractor();
    }

    /**
     * Creates the {@link BaggageValueConverterResolver} bean.
     *
     * @param beanFactory       the Spring bean factory
     * @param conversionService the Spring conversion service (optional)
     * @return the converter resolver
     */
    @Bean
    @ConditionalOnMissingBean
    public BaggageValueConverterResolver baggageValueConverterResolver(
            BeanFactory beanFactory,
            @Nullable ConversionService conversionService) {
        return new BaggageValueConverterResolver(beanFactory, conversionService);
    }

    /**
     * Creates the {@link WithBaggageAspect} bean.
     *
     * @param pathValueExtractor the path value extractor
     * @param converterResolver  the converter resolver
     * @return the aspect
     */
    @Bean
    @ConditionalOnMissingBean
    public WithBaggageAspect withBaggageAspect(
            PathValueExtractor pathValueExtractor,
            BaggageValueConverterResolver converterResolver) {
        return new WithBaggageAspect(pathValueExtractor, converterResolver);
    }
}

