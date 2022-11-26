package client2;

import io.swagger.client.ApiException;
import io.swagger.client.api.SkiersApi;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import org.apache.commons.lang.time.StopWatch;

public class Client {

  private static final LinkedBlockingQueue<Long> latencies = new LinkedBlockingQueue<>();
  private static final LinkedBlockingQueue<SkierPostRequest> requests = new LinkedBlockingQueue<>();
  private static final int numFirstPhaseThreads = 2;
  private static final int numSecondPhaseThreads = 98;
  private static final int singleThreadRequests = 1000;
  private static final int totalRequests = 100000;
  private static final int RETRIES = 5;
//  private static final String serverURL = "http://localhost:8080/Assignment1Server_war_exploded/";
//  private static final String serverURL = "http://54.190.120.149:8080/Assignment1Server_war_exploded/";
  private static final String serverURL = "http://CS6650LB-7908820.us-west-2.elb.amazonaws.com:8080/Assignment1Server_war_exploded/";
  private static ExecutorService pool;

  public static void main(String[] args) throws IOException, InterruptedException {
    long startTime = System.currentTimeMillis();
    pool = Executors.newFixedThreadPool(numFirstPhaseThreads + 1 + numSecondPhaseThreads);
    Results.count = new CountDownLatch(numFirstPhaseThreads + 1 + numSecondPhaseThreads);

    // One single thread to generate all 200,000 post requests.
    pool.execute(() -> {
      for (int i = 0; i < totalRequests; i++) {
        SkierPostRequest skierPostRequest = new SkierPostRequest();
        requests.add(skierPostRequest);
      }
      Results.count.countDown();
    });
    // first create 32 consumer thread, each send 1000 request
    for (int threadsNum = 0; threadsNum < numFirstPhaseThreads; threadsNum++) {
      CountDownLatch firstPhaseLatch = new CountDownLatch(1);
      singleThreadExecution(firstPhaseLatch);
    }

    for (int threadsNum = 0; threadsNum < numSecondPhaseThreads; threadsNum++) {
      CountDownLatch secondPhaseLatch = new CountDownLatch(numSecondPhaseThreads);
      singleThreadExecution(secondPhaseLatch);
    }
    Results.count.await();
    pool.shutdown();
    long endTime = System.currentTimeMillis();
    Long[] latenciesArray = latencies.toArray(new Long[0]);
    Arrays.sort(latenciesArray);

    System.out.println(
        "Number of Successful Requests Sent: " + Results.successCount.get() + "\n"
            + "Number of Unsuccessful Requests: " + Results.failureCount.get() + "\n"
            + "Total run time: " + (endTime - startTime) + " (ms) \n"
            + "mean response time: " + findMean(latenciesArray) + " (ms) \n"
            + "median response time: " + median(latenciesArray) + " (ms) \n"
            + "Total Throughput in requests per second: "
            + 1000.0 * (Results.failureCount.get() + Results.successCount.get()) / (endTime
            - startTime) + "\n"
            + "p99 (99th percentile) response time: "
            + percentile(latenciesArray, 99) + "(ms) \n"
            + "min response time: "
            + latenciesArray[0] + "(ms) \n"
            + "max response time: "
            + latenciesArray[latenciesArray.length - 1] + "(ms) \n"
    );
  }

  private static void singleThreadExecution(CountDownLatch countDownLatch) {
    pool.execute(() -> {
      SkiersApi api = new SkiersApi();
      api.getApiClient().setBasePath(serverURL);
      for (int i = 0; i < singleThreadRequests; i++) {
        StopWatch stopWatch = new StopWatch();
        int curTurn = 0;
        if (requests.isEmpty()) {
          break;
        }
        SkierPostRequest singleRequest = requests.poll();
        stopWatch.start();
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
        stopWatch.stop();
        if (curTurn < RETRIES) {
          Results.successCount.incrementAndGet();
        } else {
          Results.failureCount.incrementAndGet();
        }
        latencies.add(stopWatch.getTime()); // Prints: Time Elapsed: 2501
      }
      countDownLatch.countDown();
      Results.count.countDown();
    });
  }

  private static long percentile(Long[] latencies, double percentile) {
    int index = (int) Math.ceil(percentile / 100.0 * latencies.length);
    return latencies[index - 1];
  }

  private static double findMean(Long[] a) {
    long sum = 0;
    for (Long aLong : a) {
      sum += aLong;
    }
    return (double) sum / (double) a.length;
  }

  private static double median(Long[] values) {
    double median;
    int totalElements = values.length;
    if (totalElements % 2 == 0) {
      long sumOfMiddleElements = values[totalElements / 2] +
          values[totalElements / 2 - 1];
      median = ((double) sumOfMiddleElements) / 2;
    } else {
      median = (double) values[values.length / 2];
    }
    return median;
  }
}