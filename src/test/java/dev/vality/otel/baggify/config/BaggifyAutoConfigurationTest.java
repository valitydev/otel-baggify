package dev.vality.otel.baggify.config;

import dev.vality.otel.baggify.aspect.WithBaggageAspect;
import dev.vality.otel.baggify.converter.BaggageValueConverterResolver;
import dev.vality.otel.baggify.extractor.PathValueExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BaggifyAutoConfiguration Tests")
class BaggifyAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(BaggifyAutoConfiguration.class));

    @Test
    @DisplayName("Should create all beans when OpenTelemetry is on classpath")
    void shouldCreateAllBeansWhenOpenTelemetryIsOnClasspath() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(PathValueExtractor.class);
            assertThat(context).hasSingleBean(BaggageValueConverterResolver.class);
            assertThat(context).hasSingleBean(WithBaggageAspect.class);
        });
    }

    @Test
    @DisplayName("Should allow custom PathValueExtractor bean")
    void shouldAllowCustomPathValueExtractorBean() {
        PathValueExtractor customExtractor = new PathValueExtractor();

        contextRunner
                .withBean(PathValueExtractor.class, () -> customExtractor)
                .run(context -> {
                    assertThat(context).hasSingleBean(PathValueExtractor.class);
                    assertThat(context.getBean(PathValueExtractor.class)).isSameAs(customExtractor);
                });
    }

    @Test
    @DisplayName("Should allow custom BaggageValueConverterResolver bean")
    void shouldAllowCustomBaggageValueConverterResolverBean() {
        contextRunner
                .withBean(BaggageValueConverterResolver.class, () -> 
                        new BaggageValueConverterResolver(null, null))
                .run(context -> {
                    assertThat(context).hasSingleBean(BaggageValueConverterResolver.class);
                });
    }

    @Test
    @DisplayName("Should allow custom WithBaggageAspect bean")
    void shouldAllowCustomWithBaggageAspectBean() {
        contextRunner
                .withBean(WithBaggageAspect.class, () -> 
                        new WithBaggageAspect(new PathValueExtractor(), 
                                new BaggageValueConverterResolver(null, null)))
                .run(context -> {
                    assertThat(context).hasSingleBean(WithBaggageAspect.class);
                });
    }
}

