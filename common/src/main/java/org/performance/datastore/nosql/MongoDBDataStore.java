package org.performance.datastore.nosql;

import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.UuidRepresentation;
import org.bson.codecs.UuidCodec;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.performance.datastore.AbstractDataStore;
import org.testcontainers.mongodb.MongoDBContainer;

import java.util.Arrays;

public class MongoDBDataStore extends AbstractDataStore<MongoDBContainer> {

    private MongoClient mongoClient;


    public MongoDBDataStore() {
        super(new MongoDBContainer("mongo:7.0.16"));
    }

    @Override
    public void stop() {
        this.mongoClient.close();
        super.stop();
    }

    public MongoClient mongoClient() {
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
