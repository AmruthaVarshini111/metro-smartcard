package com.metro.api;

import com.metro.domain.CardRepository;
import com.metro.domain.FareCalculator;
import com.metro.domain.FarePolicy;
import com.metro.domain.InMemoryCardRepository;
import com.metro.domain.MetroNetwork;
import com.metro.domain.RoutePlanner;
import com.metro.domain.SampleNetwork;
import com.metro.domain.TapService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the framework-free domain classes into Spring. The domain itself has no annotations. */
@Configuration
class AppConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    MetroNetwork metroNetwork() {
        return SampleNetwork.build();
    }

    @Bean
    FarePolicy farePolicy() {
        return FarePolicy.defaults();
    }

    @Bean
    RoutePlanner routePlanner(MetroNetwork network) {
        return new RoutePlanner(network);
    }

    @Bean
    FareCalculator fareCalculator(FarePolicy policy) {
        return new FareCalculator(policy);
    }

    @Bean
    CardRepository cardRepository() {
        return new InMemoryCardRepository();
    }

    @Bean
    TapService tapService(MetroNetwork network, RoutePlanner planner, FareCalculator fares,
                          FarePolicy policy, CardRepository cards) {
        return new TapService(network, planner, fares, policy, cards);
    }
}
