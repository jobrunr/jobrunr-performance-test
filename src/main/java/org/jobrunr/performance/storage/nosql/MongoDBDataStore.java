package org.jobrunr.performance.storage.nosql;

import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.UuidRepresentation;
import org.bson.codecs.UuidCodec;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.jobrunr.performance.storage.DataStore;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.nosql.mongo.MongoDBStorageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.MongoDBContainer;

import java.util.Arrays;

public class MongoDBDataStore implements DataStore {

    protected final Logger LOGGER = LoggerFactory.getLogger(MongoDBDataStore.class);

    private final MongoDBContainer container;
    private MongoClient mongoClient;


    public MongoDBDataStore() {
        this.container = new MongoDBContainer("mongo:7.0.16");
    }

    @Override
    public void start() {
        this.container.start();
    }

    @Override
    public void stop() {
        this.mongoClient.close();
        this.container.stop();
    }

    @Override
    public StorageProvider getStorageProvider(boolean logQueries) {
        // TODO: log queries for MongoDB somehow
        return new MongoDBStorageProvider(mongoClient());
    }

    protected MongoClient mongoClient() {
        if (mongoClient == null) {
            CodecRegistry codecRegistry = CodecRegistries.fromRegistries(
                    CodecRegistries.fromCodecs(new UuidCodec(UuidRepresentation.STANDARD)),
                    MongoClientSettings.getDefaultCodecRegistry()
            );
            mongoClient = MongoClients.create(
                    MongoClientSettings.builder()
                            .applyToClusterSettings(builder -> builder.hosts(Arrays.asList(new ServerAddress(container.getHost(), container.getMappedPort(27017)))))
                            .codecRegistry(codecRegistry)
                            .build());

        }
        return mongoClient;
    }
}
