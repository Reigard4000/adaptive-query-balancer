package com.mullagaliev.aqpbalancer;

import com.mullagaliev.aqpbalancer.controller.SimulationController;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        new SimulationController().run();
    }
}
