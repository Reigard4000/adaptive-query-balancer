package com.mullagaliev.aqpbalancer.service.execution;

import com.mullagaliev.aqpbalancer.model.QueryContext;
import com.mullagaliev.aqpbalancer.model.QueryRequest;
import com.mullagaliev.aqpbalancer.model.SampleResult;

public interface SamplingStrategy {

    SampleResult execute(QueryRequest request, QueryContext context);
}
