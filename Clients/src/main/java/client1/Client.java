package client1;

import io.swagger.client.ApiException;
import io.swagger.client.api.SkiersApi;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import org.apache.commons.lang.time.StopWatch;

public class Client {

  private static final LinkedBlockingQueue<Long> latencies = new LinkedBlockingQueue<>();
  private static final LinkedBlockingQueue<SkierPostRequest> requests = new LinkedBlockingQueue<>();
  private static final int numFirstPhaseThreads = 32;
  private static final int numSecondPhaseThreads = 200;
  private static final int singleThreadRequests = 10000;
  private static final int totalRequests = 100000;
  private static final int RETRIES = 5;
  private static final boolean isLittleLawThroughputPrediction = false;
  private static final String serverURL = "http://localhost:8080/Assignment1Server_war_exploded/";
//  private static final String serverURL = "http://54.190.120.149:8080/Assignment1Server_war_exploded/";
  private static ExecutorService pool;

  public static void main(String[] args) throws IOException, InterruptedException {
    long startTime = System.currentTimeMillis();
    pool = Executors.newFixedThreadPool(numFirstPhaseThreads + 1 + numSecondPhaseThreads);
    Results.count = new CountDownLatch(numFirstPhaseThreads + 1 + numSecondPhaseThreads);
    // One single thread to generate all 200,000 post requests.
    pool.execute(new Runnable() {
      @Override
      public void run() {
        for (int i = 0; i < totalRequests; i++) {
          SkierPostRequest skierPostRequest = new SkierPostRequest();
          requests.add(skierPostRequest);
        }
        Results.count.countDown();
      }
    });

    if (isLittleLawThroughputPrediction) {
      System.out.println("Ready to test latency!");
      long startTimeTest = System.currentTimeMillis();
      CountDownLatch testLatch = new CountDownLatch(1);
      singleThreadExecution(testLatch);
      testLatch.await();
      long endTimeTest = System.currentTimeMillis();
      System.out.println("Total Duration is " + 1.0*(endTimeTest - startTimeTest)/1000 + " with average latency about " + 1.0*(endTimeTest - startTimeTest)/singleThreadRequests);
      System.out.println("Number of Successful Requests Sent: " + Results.successCount.get() + "\n"
          + "Number of Unsuccessful Requests: " + Results.failureCount.get() + "\n");
    } else {
      //first phase is creating 32 threads, each send 1000 request
      CountDownLatch firstPhaseLatch = new CountDownLatch(1);
      for (int threadsNum = 0; threadsNum < numFirstPhaseThreads; threadsNum++) {
        singleThreadExecution(firstPhaseLatch);
      }

      for (int threadsNum = 0; threadsNum < numSecondPhaseThreads; threadsNum++) {
        CountDownLatch secondPhaseLatch = new CountDownLatch(numSecondPhaseThreads);
        singleThreadExecution(secondPhaseLatch);
      }
      Results.count.await();
      pool.shutdown();
      long endTime = System.currentTimeMillis();
      System.out.println(
          "Number of Successful Requests Sent: " + Results.successCount.get() + "\n"
              + "Number of Unsuccessful Requests: " + Results.failureCount.get() + "\n"
              + "Total run time: " + (endTime - startTime) + " (ms) \n"
              + "Total Throughput in requests per second: "
              + 1000.0 * (Results.failureCount.get() + Results.successCount.get()) / (endTime
              - startTime) + "\n"
      );
    }
    System.exit(0);
  }

  private static void singleThreadExecution(CountDownLatch countDownLatch) {
    pool.execute(() -> {
      SkiersApi api = new SkiersApi();
      api.getApiClient().setBasePath(serverURL);
      for (int i = 0; i < singleThreadRequests; i++) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        int curTurn = 0;
        if (requests.isEmpty()) {
          break;
        }
        SkierPostRequest singleRequest = requests.poll();
        while (curTurn < RETRIES) {
          try {
            api.writeNewLiftRide(singleRequest.liftRide,
                singleRequest.resortID,
                singleRequest.seasonID,
                singleRequest.dayID,
                singleRequest.skierID
            );
            break;
          } catch (ApiException e) {
            curTurn++;
            try {
              Thread.sleep(5);
            } catch (InterruptedException ex) {
              ex.printStackTrace();
            }
          }
        }
        if (curTurn < RETRIES) {
          Results.successCount.incrementAndGet();
        } else {
          Results.failureCount.incrementAndGet();
        }
        stopWatch.stop();
        latencies.add(stopWatch.getTime()); // Prints: Time Elapsed: 2501
      }
      countDownLatch.countDown();
      Results.count.countDown();
    });
  }
}