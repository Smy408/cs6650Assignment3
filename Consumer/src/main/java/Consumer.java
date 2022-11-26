import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeoutException;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Protocol;

public class Consumer {

  private final static String QUEUE_NAME = "ride";
  private final static int THREAD_POOL_SIZE = 3;
  private static final String IP = "35.91.164.14";
  private static final int PORT = 5672;
  private static final String USER = "username";
  private static final String PASSWORD = "password";

  protected static final JedisPool pool =
      new JedisPool(getPoolConfig(), Protocol.DEFAULT_HOST, 6379);

  public static void main(String[] args) throws IOException, TimeoutException {
    Gson gson = new Gson();
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost(IP);
    factory.setPort(PORT);
    factory.setUsername(USER);
    factory.setPassword(PASSWORD);
    Connection connection = factory.newConnection();


    Runnable runnable = () -> {
      try {
        final Channel channel = connection.createChannel();
        channel.queueDeclare(QUEUE_NAME, false, false, false, null);
        System.out.println(
            " [*] Thread " + Thread.currentThread().getId() + "awaiting requests");

        final DeliverCallback callback =
            (consumerTag, delivery) -> {
              String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
              JsonObject body = gson.fromJson(message, JsonObject.class);
              String skierID = body.get("skierID").getAsString();
              JsonObject data = new JsonObject();
              data.add("liftID", body.get("liftID"));
              data.add("time", body.get("time"));
              data.add("dayID", body.get("dayId"));
              data.add("resortID", body.get("resortID"));
              data.add("seasonID", body.get("seasonID"));
              try (Jedis jedis = pool.getResource()) {
                jedis.sadd(String.valueOf(skierID), gson.toJson(data));
              }
              channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            };
        channel.basicConsume(QUEUE_NAME, false, callback, consumerTag -> {
        });
      } catch (IOException e) {
        e.printStackTrace();
      }
    };

    for (int i = 0; i < THREAD_POOL_SIZE; i++) {
      Thread thread = new Thread(runnable);
      thread.start();
    }

    System.out.println("[x] Connection is ready, " + THREAD_POOL_SIZE +
        " Thread waiting for messages. To exit press CTRL+C\"");
  }

  private static JedisPoolConfig getPoolConfig() {
    JedisPoolConfig config = new JedisPoolConfig();
    config.setMaxTotal(128);
    return config;
  }
}
