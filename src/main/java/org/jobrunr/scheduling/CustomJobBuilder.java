package org.jobrunr.scheduling;

import org.jobrunr.jobs.states.InitialState;

public class CustomJobBuilder {

    public static JobBuilder setInitialState(JobBuilder jobBuilder, InitialState initialState) {
        jobBuilder.setInitialState(initialState);
        return jobBuilder;
    }
}
