package com.glory.spotflow_wallet.spotflow;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Single place that decides which SpotflowClient implementation is active.
 * Everything outside the `spotflow` package depends only on the SpotflowClient interface.
 */
@Configuration
@EnableConfigurationProperties(SpotflowProperties.class)
public class SpotflowConfig {

    @Bean
    public SpotflowClient spotflowClient(SpotflowProperties properties) {
        if (properties.isMock()) {
            return new MockSpotflowClient();
        }
        return new HttpSpotflowClient(properties);
    }
}
