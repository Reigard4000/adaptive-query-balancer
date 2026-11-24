package com.mullagaliev.aqpbalancer.config;

import com.mullagaliev.aqpbalancer.model.BackendServer;
import com.mullagaliev.aqpbalancer.service.LoadBalancerCore;
import com.mullagaliev.aqpbalancer.service.balancer.AdaptiveLoadBalancer;
import com.mullagaliev.aqpbalancer.service.balancer.ServerPicker;
import com.mullagaliev.aqpbalancer.service.decision.StrategyPolicy;
import com.mullagaliev.aqpbalancer.service.decision.ThompsonSampler;
import com.mullagaliev.aqpbalancer.service.generation.QueryGenerator;
import com.mullagaliev.aqpbalancer.service.learning.BanditUpdater;
import com.mullagaliev.aqpbalancer.service.learning.RewardCalculator;
import com.mullagaliev.aqpbalancer.service.phase.EnsembleTailIndexEstimator;
import com.mullagaliev.aqpbalancer.service.phase.LatencyWindow;
import com.mullagaliev.aqpbalancer.service.phase.PhaseTransitionDetector;
import com.mullagaliev.aqpbalancer.service.quality.BackendAccuracyProvider;

import java.util.List;

/**
 * Wires all components together. The same core configuration is shared by the
 * real client-facing runtime and by the offline console simulation.
 */
public final class AppConfig {

    private AppConfig() {
    }

    public static CoreComponents createCore(long seed) {
        return createCore(seed, AdaptiveConfig.defaults());
    }

    public static CoreComponents createCore(long seed, AdaptiveConfig config) {
        LatencyWindow latencyWindow = new LatencyWindow(config.latencyWindowSize());
        EnsembleTailIndexEstimator estimator = new EnsembleTailIndexEstimator();
        PhaseTransitionDetector detector = new PhaseTransitionDetector(
                config.detectorMinSamples(),
                config.detectorMaxHistory(),
                config.cusumBaseline(),
                config.cusumDrift(),
                config.cusumThreshold()
        );
        StrategyPolicy policy = new StrategyPolicy();
        ThompsonSampler sampler = new ThompsonSampler(seed + 3L);

        RewardCalculator rewardCalculator = new RewardCalculator(
                config.rewardAccuracyWeight(),
                config.rewardLatencyWeight(),
                config.rewardLatencyScaleMs()
        );
        BanditUpdater banditUpdater = new BanditUpdater(sampler, rewardCalculator);

        LoadBalancerCore core = new LoadBalancerCore(
                latencyWindow,
                estimator,
                detector,
                policy,
                sampler,
                banditUpdater,
                new BackendAccuracyProvider(),
                config
        );

        return new CoreComponents(
                core,
                latencyWindow,
                estimator,
                detector
        );
    }

    public static SimulationComponents create(BalancerConfig config, List<BackendServer> servers) {
        long seed = config.seed();
        CoreComponents coreComponents = createCore(seed);

        ServerPicker serverPicker = new ServerPicker();
        AdaptiveLoadBalancer balancer = new AdaptiveLoadBalancer(
                coreComponents.core(),
                serverPicker,
                servers
        );
        QueryGenerator generator = new QueryGenerator(seed + 1L);

        return new SimulationComponents(
                balancer,
                coreComponents.core(),
                generator,
                coreComponents.latencyWindow(),
                coreComponents.estimator(),
                coreComponents.detector()
        );
    }

    public record CoreComponents(
            LoadBalancerCore core,
            LatencyWindow latencyWindow,
            EnsembleTailIndexEstimator estimator,
            PhaseTransitionDetector detector
    ) {
    }

    public record SimulationComponents(
            AdaptiveLoadBalancer balancer,
            LoadBalancerCore core,
            QueryGenerator generator,
            LatencyWindow latencyWindow,
            EnsembleTailIndexEstimator estimator,
            PhaseTransitionDetector detector
    ) {
    }
}
