package com.mullagaliev.aqpbalancer.model;

public record QueryContext(
        String alphaBucket,
        QueryType queryType,
        double skewness,
        int hourOfDay,
        double platformLoad
) {

    public String key() {
        return alphaBucket;
    }
}
