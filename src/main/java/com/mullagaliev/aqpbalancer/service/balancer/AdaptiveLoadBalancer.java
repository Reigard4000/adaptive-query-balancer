package com.mullagaliev.aqpbalancer.service.balancer;

import com.mullagaliev.aqpbalancer.exception.BalancerException;
import com.mullagaliev.aqpbalancer.model.BackendServer;
import com.mullagaliev.aqpbalancer.model.QueryRequest;
import com.mullagaliev.aqpbalancer.model.QueryResponse;
import com.mullagaliev.aqpbalancer.model.ServerExecutionResult;
import com.mullagaliev.aqpbalancer.service.LoadBalancerCore;

import java.util.List;
import java.util.Objects;

/**
 * Two-level balancer: ServerPicker decides <em>where</em> to send a request,
 * LoadBalancerCore decides <em>how</em> to execute it.
 */
public class AdaptiveLoadBalancer {

    private final LoadBalancerCore core;
    private final ServerPicker serverPicker;
    private final List<BackendServer> servers;

    public AdaptiveLoadBalancer(LoadBalancerCore core, ServerPicker serverPicker, List<BackendServer> servers) {
        this.core = Objects.requireNonNull(core, "core");
        this.serverPicker = Objects.requireNonNull(serverPicker, "serverPicker");
        if (servers == null || servers.isEmpty()) {
            throw new IllegalArgumentException("At least one backend server is required");
        }
        this.servers = List.copyOf(servers);
    }

    public QueryResponse dispatch(QueryRequest request) {
        BackendServer server = serverPicker.pick(servers);
        server.onRequestDispatched();

        long startedNanos = System.nanoTime();
        boolean recorded = false;
        try {
            LoadBalancerCore.PreparedExecution plan = core.prepare(request);
            ServerExecutionResult execution = server.execute(request, plan.phase(), plan.strategy());

            server.recordCompletion(execution);
            recorded = true;

            return core.complete(plan, server.getId(), execution);
        } catch (RuntimeException exception) {
            if (!recorded) {
                double latencyMs = (System.nanoTime() - startedNanos) / 1_000_000.0;
                server.recordCompletion(ServerExecutionResult.failure(
                        latencyMs,
                        0,
                        exception.getMessage() == null ? "Ошибка выполнения" : exception.getMessage()
                ));
            }
            if (exception instanceof BalancerException) {
                throw exception;
            }
            throw new BalancerException("Failed to dispatch request " + request.id(), exception);
        }
    }

    public LoadBalancerCore getCore() {
        return core;
    }

    public List<BackendServer> getServers() {
        return servers;
    }
}
