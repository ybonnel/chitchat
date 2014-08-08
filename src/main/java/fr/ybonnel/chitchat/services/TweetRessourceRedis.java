package fr.ybonnel.chitchat.services;

import com.mongodb.MongoClient;
import fr.ybonnel.chitchat.model.Tweet;
import org.boon.json.JsonFactory;
import org.boon.json.ObjectMapper;
import org.jongo.Jongo;
import org.jongo.MongoCollection;
import redis.clients.jedis.Jedis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public enum TweetRessourceRedis {

    INSTANCE;

    private ScheduledExecutorService executorServiceForInsert = Executors.newSingleThreadScheduledExecutor();
    private ExecutorService executorService = Executors.newFixedThreadPool(10);
    private final List<Tweet> insertQueue = new ArrayList<>();

    private final ThreadLocal<Jedis> jedis = ThreadLocal.withInitial(() -> new Jedis("localhost"));
    private final ThreadLocal<ObjectMapper> mapper = ThreadLocal.withInitial(JsonFactory::create);

    private MongoCollection mongoCollection;

    public void setMongoClient(MongoClient mongoClient) {
        Jongo jongo = new Jongo(mongoClient.getDB("chitchat"));
        mongoCollection = jongo.getCollection("tweets");
        mongoCollection.ensureIndex("{text:\"text\"}");

        executorServiceForInsert.scheduleAtFixedRate(() -> {
            Tweet[] tweets;
            synchronized (insertQueue) {
                if (insertQueue.isEmpty()) {
                    return;
                }
                tweets = insertQueue.toArray(new Tweet[insertQueue.size()]);
                insertQueue.clear();
            }
            mongoCollection.insert(tweets);
        }, 0, 1, TimeUnit.SECONDS);
    }

    public void save(Tweet tweet) {
        try {
            tweet.createdAt = Instant.now().toEpochMilli();
            String document = mapper.get().toJson(tweet);
            // For latest
            jedis.get().set(tweet.author, document);
            // For thread
            jedis.get().rpush(Long.toString(tweet.thread), document);
            synchronized (insertQueue) {
                insertQueue.add(tweet);
            }

        } catch (RuntimeException exception) {
            exception.printStackTrace();
            throw  exception;
        }
    }

    public String latestOfHero(String author) {
        return jedis.get().get(author);
    }

    public String thread(String thread) {
        return jedis.get().lrange(thread, 0, -1)
                .stream()
                .collect(Collectors.joining(",", "[", "]"));
    }

    public List<Tweet> search(String q) {
        try {
            return StreamSupport.stream(
                    mongoCollection.find("{$text: {$search:#}}", q)
                            .as(Tweet.class).spliterator(),
                    false
            ).collect(Collectors.toList());
        } catch (RuntimeException exception) {
            exception.printStackTrace();
            throw  exception;
        }
    }
}
