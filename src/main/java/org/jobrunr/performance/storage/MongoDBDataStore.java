package org.jobrunr.performance.storage;

import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.UuidRepresentation;
import org.bson.codecs.UuidCodec;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.nosql.mongo.MongoDBStorageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.MongoDBContainer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

import static org.jobrunr.storage.StorageProviderUtils.Jobs.FIELD_UPDATED_AT;

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

    @Override
    public Instant getUpdatedAtOfLastSucceededJob() {
        MongoDatabase jobrunrDatabase = mongoClient.getDatabase("jobrunr");
        FindIterable<Document> lastSucceededJob = jobrunrDatabase.getCollection("jobs").find().sort(Sorts.descending(FIELD_UPDATED_AT)).limit(1);
        Long microseconds = lastSucceededJob.first().getLong(FIELD_UPDATED_AT);
        return Instant.EPOCH.plus(microseconds, ChronoUnit.MICROS);
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
