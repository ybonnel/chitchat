package fr.ybonnel.chitchat;

import com.github.kevinsawicki.http.HttpRequest;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mongodb.MongoClient;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;
import fr.ybonnel.chitchat.model.Tweet;
import fr.ybonnel.chitchat.services.TweetRessourceRedis;
import io.undertow.Undertow;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ServerTest {

    private int port;
    private static MongodExecutable mongodExecutable;
    private Undertow server;

    public ServerTest() {
        Random random = new Random();
        port = Integer.getInteger("test.http.port", random.nextInt(10000) + 10000);
    }

    protected String defaultUrl() {
        return "http://127.0.0.1:" + port;
    }

    private static int mongoPort;

    @BeforeClass
    public static void startMongo() throws IOException {
        MongodStarter starter = MongodStarter.getDefaultInstance();

        Random random = new Random();
        mongoPort = random.nextInt(10000) + 20000;

        mongodExecutable = starter.prepare(
                new MongodConfigBuilder()
                        .version(Version.V2_6_1)
                        .net(new Net(mongoPort, Network.localhostIsIPv6()))
                        .build());

        mongodExecutable.start();
    }

    @AfterClass
    public static void stopMongo() {
        mongodExecutable.stop();
    }

    @Before
    public void setup() throws IOException {

        Jedis jedis = new Jedis("localhost");
        jedis.keys("*")
                .stream()
                .forEach(
                        jedis::del
                );

        MongoClient mongoClient = new MongoClient("localhost", mongoPort);
        mongoClient.getDB("chitchat").getCollection("tweets").drop();

        TweetRessourceRedis.INSTANCE.setMongoClient(
                mongoClient
        );
        server = UndertowServer.startServer(port);
    }

    @After
    public void tearDown() throws IOException {
        server.stop();

        Jedis jedis = new Jedis("localhost");
        jedis.keys("*")
                .stream()
                .forEach(
                        jedis::del
                );

    }

    @Test
    public void can_post_a_tweet() {
        createTweet("Loki", "I Have an army !", 3);
    }

    private void createTweet(String author, String text, long thread) {
        Tweet tweet = new Tweet();
        tweet.author = author;
        tweet.text = text;
        tweet.thread = thread;

        Gson gson = new Gson();
        assertEquals(201, HttpRequest.post(defaultUrl() + "/chitchat").send(gson.toJson(tweet)).code());
    }

    @Test
    public void can_get_latest_tweet() throws InterruptedException {
        createTweet("Iron Man", "We have a Hulk !", 3);


        Gson gson = new Gson();
        HttpRequest request = HttpRequest.get(defaultUrl() + "/chitchat/latest/Iron%20Man");
        assertEquals(200, request.code());
        Tweet tweet = gson.fromJson(request.body(), Tweet.class);
        assertEquals("Iron Man", tweet.author);
        assertEquals("We have a Hulk !", tweet.text);
        assertEquals(3, tweet.thread);
        assertNotNull(tweet.createdAt);
    }

    @Test
    public void can_get_a_thread() throws InterruptedException {
        createTweet("Loki", "I have an army !", 3);
        createTweet("Iron Man", "We have a Hulk !", 3);
        createTweet("Superman", "I'm in an other world !", 1);

        Gson gson = new Gson();
        HttpRequest request = HttpRequest.get(defaultUrl() + "/chitchat/thread/3");


        assertEquals(200, request.code());
        List<Tweet> tweets = gson.fromJson(request.body(), new TypeToken<List<Tweet>>(){}.getType());
        assertEquals(2, tweets.size());

        Tweet tweet1 = tweets.get(0);
        assertEquals("Loki", tweet1.author);
        assertEquals("I have an army !", tweet1.text);
        assertEquals(3, tweet1.thread);
        assertNotNull(tweet1.createdAt);

        Tweet tweet2 = tweets.get(1);
        assertEquals("Iron Man", tweet2.author);
        assertEquals("We have a Hulk !", tweet2.text);
        assertEquals(3, tweet2.thread);
        assertNotNull(tweet2.createdAt);
    }

    @Test
    public void can_search() throws InterruptedException {
        createTweet("Loki", "I have an army !", 3);
        createTweet("Iron Man", "We have a Hulk !", 3);
        createTweet("Superman", "I'm in an other world !", 1);
        createTweet("Black Widow", "Loki means to unleash the HULK. Keep Banner in the lab, I'm on my way.", 2);

        Thread.sleep(1000);

        Gson gson = new Gson();
        HttpRequest request = HttpRequest.get(defaultUrl() + "/chitchat/search", true, "q", "hulk");


        assertEquals(200, request.code());
        List<Tweet> tweets = gson.fromJson(request.body(), new TypeToken<List<Tweet>>(){}.getType());
        assertEquals(2, tweets.size());

        Tweet ironMan = tweets.stream().filter(tweet -> tweet.author.equals("Iron Man")).findFirst().get();
        Tweet blackWidow = tweets.stream().filter(tweet -> tweet.author.equals("Black Widow")).findFirst().get();

        assertEquals("Iron Man", ironMan.author);
        assertEquals("We have a Hulk !", ironMan.text);
        assertEquals(3, ironMan.thread);
        assertNotNull(ironMan.createdAt);

        assertEquals("Black Widow", blackWidow.author);
        assertEquals("Loki means to unleash the HULK. Keep Banner in the lab, I'm on my way.", blackWidow.text);
        assertEquals(2, blackWidow.thread);
        assertNotNull(blackWidow.createdAt);

    }
}
