package fr.ybonnel.chitchat;

import com.mongodb.MongoClient;
import fr.ybonnel.chitchat.model.Tweet;
import fr.ybonnel.chitchat.services.TweetRessourceRedis;
import io.undertow.Undertow;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import org.apache.commons.io.IOUtils;
import org.boon.json.JsonFactory;
import org.boon.json.ObjectMapper;

import java.io.IOException;
import java.net.UnknownHostException;

public class UndertowServer {

    // Boon
    private static ThreadLocal<ObjectMapper> mapper = ThreadLocal.withInitial(JsonFactory::create);
    private static HttpString POST = HttpString.tryFromString("POST");
    private static HttpString GET = HttpString.tryFromString("GET");

    public static void handleRequest(final HttpServerExchange exchange)  {
        try {
            if (exchange.getRequestMethod().equals(POST)) {
                if (exchange.getRequestPath().equals("/chitchat")) {
                    exchange.startBlocking();
                    String json = IOUtils.toString(exchange.getInputStream(), "utf-8");
                    Tweet tweet = mapper.get().fromJson(json, Tweet.class);
                    TweetRessourceRedis.INSTANCE.save(tweet);
                    exchange.setResponseCode(201);
                    exchange.getResponseSender().close();
                } else {
                    exchange.setResponseCode(404);
                    exchange.getResponseSender().close();
                }
            } else if (exchange.getRequestMethod().equals(GET)) {
                if (exchange.getRelativePath().startsWith("/chitchat/latest/")) {
                    String[] pathParams = exchange.getRelativePath().split("/");
                    String hero = pathParams[pathParams.length - 1];
                    String tweet = TweetRessourceRedis.INSTANCE.latestOfHero(hero);
                    if (tweet == null) {
                        exchange.setResponseCode(404);
                        exchange.getResponseSender().close();
                    } else {
                        exchange.setResponseCode(200);
                        exchange.getResponseSender().send(
                                tweet
                        );
                    }
                } else if (exchange.getRelativePath().startsWith("/chitchat/thread/")) {
                    String[] pathParams = exchange.getRelativePath().split("/");
                    String thread = pathParams[pathParams.length - 1];
                    exchange.setResponseCode(200);
                    exchange.getResponseSender().send(
                            TweetRessourceRedis.INSTANCE.thread(thread)
                    );
                } else if (exchange.getRelativePath().startsWith("/chitchat/search")) {
                    exchange.setResponseCode(200);
                    exchange.getResponseSender().send(
                            mapper.get().toJson(
                                    TweetRessourceRedis.INSTANCE.search(
                                            exchange.getQueryParameters().get("q").getFirst()
                                    )
                            )
                    );
                } else {
                    exchange.setResponseCode(404);
                    exchange.getResponseSender().close();
                }
            } else {
                exchange.setResponseCode(404);
                exchange.getResponseSender().close();
            }
        } catch (IOException ignore){}
    }

    public static void main(String[] args) throws UnknownHostException {
        TweetRessourceRedis.INSTANCE.setMongoClient(new MongoClient());
        startServer(9999);

    }

    public static Undertow startServer(int port) {
        Undertow server = Undertow.builder()
                .addHttpListener(port, "localhost")
                .setHandler(UndertowServer::handleRequest).build();
        server.start();
        return server;
    }

}
