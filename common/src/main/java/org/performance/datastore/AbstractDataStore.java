package org.performance.datastore;

import org.performance.utils.Memory;
import org.testcontainers.containers.GenericContainer;

import static org.performance.utils.Memory.Unit.gigabytes;

public class AbstractDataStore<T extends GenericContainer<T>> implements DataStore {

    protected final T container;

    public AbstractDataStore(T container) {
        this.container = container;
    }

    @Override
    public String getNameAndVersion() {
        return container.getDockerImageName();
    }

    @Override
    public void start() {
        container.setShmSize(Memory.of(2, gigabytes).toBytes());
        container.start();
    }

    @Override
    public void stop() {
        container.stop();
    }

    public T getContainer() {
        return container;
    }
}
