package me.sharuru.serverchecker;

import com.opencsv.bean.CsvToBeanBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Slf4j
@Component
public class ServerCheckRunner implements CommandLineRunner {

    private ConcurrentHashMap<String, CheckResultModel> checkResults = new ConcurrentHashMap<>();
    private List<CompletableFuture<Integer>> taskFutures = new ArrayList<>();

    @Value("${app.thread-pool-size:3}")
    private int threadpoolSize;
    @Value("${app.ping-times:5}")
    private int pingTimes;
    @Value("${app.connection-timeout:2000}")
    private int connectionTimeout;
    @Value("${app.file-path:NULL}")
    private String configuredFilePath;

    @Override
    public void run(String... args) throws Exception {
        log.info("===== JOB STARTED =====");

        log.info("Step 0. Read setting file.");
        final String FILE_PATH = "NULL".equals(configuredFilePath) ? Paths.get("").toAbsolutePath().toString() + File.separator + "hosts.csv" : configuredFilePath;
        @SuppressWarnings("unchecked")
        List<HostsModel> hostList = new CsvToBeanBuilder(Files.newBufferedReader(Paths.get((FILE_PATH)))).withType(HostsModel.class).build().parse();

        ExecutorService executor = Executors.newFixedThreadPool(threadpoolSize);

        log.info("Step 1. Check server using Ping.");
        for (HostsModel model : hostList) {
            CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
                try {
                    long latency = 0L;
                    int errorTimes = 0;
                    log.info("Now ping check on: {}, {}", model.getHost(), model.getMemo());
                    for (int i = 0; i < pingTimes; i++) {
                        long currentTime = System.currentTimeMillis();
                        boolean isPinged;
                        isPinged = InetAddress.getByName(model.getHost()).isReachable(connectionTimeout);
                        currentTime = System.currentTimeMillis() - currentTime;
                        if (isPinged) {
                            latency += currentTime;
                        } else {
                            errorTimes++;
                        }
                        TimeUnit.SECONDS.sleep(1L);
                    }
                    if (errorTimes == pingTimes) {
                        CheckResultModel result = new CheckResultModel();
                        result.setBase(model);
                        result.setPingTestResult(false);
                        result.setPingTestMemo("Ping check is failed, 0/" + pingTimes + ".");
                        checkResults.putIfAbsent(model.getIdentify(), result);
                    } else {
                        CheckResultModel result = new CheckResultModel();
                        result.setBase(model);
                        result.setPingTestResult(true);
                        result.setPingTestMemo("Ping check passed, average latency: " + (double) latency / (pingTimes - errorTimes) + "ms, " + (pingTimes - errorTimes) + "/" + pingTimes);
                        checkResults.putIfAbsent(model.getIdentify(), result);
                    }
                } catch (Exception e) {
                    log.error("Exception happened in ping check. {}", e.getMessage());
                    return -1;
                }

                return 0;

            }, executor);
            taskFutures.add(future);
        }

        // wait all task finished
        CompletableFuture.allOf(taskFutures.toArray(new CompletableFuture[0])).join();

        log.info("Step 2. Port check");
        for (HostsModel model : hostList) {
            CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
                Socket socketClient = new Socket();
                try {
                    log.info("Now port check on: {}, {}", model.getHost(), model.getMemo());
                    socketClient.connect(new InetSocketAddress(model.getHost(), model.getPort()), connectionTimeout);
                    socketClient.setSoTimeout(connectionTimeout);
                    checkResults.get(model.getIdentify()).setPortTestResult(true);
                    checkResults.get(model.getIdentify()).setPortTestMemo("Port check passed.");
                } catch (Exception e) {
                    checkResults.get(model.getIdentify()).setPortTestResult(false);
                    checkResults.get(model.getIdentify()).setPortTestMemo("Port check failed. " + e.getMessage());
                } finally {
                    try {
                        socketClient.close();
                    } catch (Exception e) {
                        log.error("Exception happened in port check. {}" + e.getMessage());
                    }
                }
                return 0;
            }, executor);
            taskFutures.add(future);
        }

        // wait all task finished
        CompletableFuture.allOf(taskFutures.toArray(new CompletableFuture[0])).join();

        log.info("Step 3. HTTP status code check");
        for (HostsModel model : hostList) {
            CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
                HttpURLConnection connection = null;
                try {
                    log.info("Now http check on: {}, {}", model.getUrl(), model.getMemo());
                    URL url = new URL(model.getUrl());
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(connectionTimeout * 2);
                    connection.setReadTimeout(connectionTimeout * 2);
                    connection.connect();
                    if(400 <= connection.getResponseCode() && connection.getResponseCode() <=499){
                        checkResults.get(model.getIdentify()).setHttpTestResult(false);
                        checkResults.get(model.getIdentify()).setHttpTestMemo("Http check failed. Status code: " + connection.getResponseCode());
                    }else{
                        checkResults.get(model.getIdentify()).setHttpTestResult(true);
                        checkResults.get(model.getIdentify()).setHttpTestMemo("Http check passed. Status code: " + connection.getResponseCode());
                    }
                    connection.disconnect();
                } catch (Exception e) {
                    checkResults.get(model.getIdentify()).setHttpTestResult(false);
                    checkResults.get(model.getIdentify()).setHttpTestMemo("Http check failed. " + e.getMessage());
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
                return 0;
            }, executor);
            taskFutures.add(future);
        }

        // wait all task finished
        CompletableFuture.allOf(taskFutures.toArray(new CompletableFuture[0])).join();

        // results
        checkResults.forEach((k, v) ->
            log.info("\n " +
                        "Host: {} / Port: {} / Url: {} / Memo: {} \n " +
                        "Check result(Ping/Port/HTTP): {} / {} / {} \n " +
                        "Info: \n" +
                        " {} \n" +
                        " {} \n" +
                        " {} \n",
                    v.getHost(), v.getPort(), v.getUrl(),
                    v.getMemo(), v.isPingTestResult() ? "OK" : "NG",v.isPortTestResult() ? "OK" : "NG",v.isHttpTestResult() ? "OK" : "NG",
                    v.getPingTestMemo(),
                    v.getPortTestMemo(),
                    v.getHttpTestMemo())
        );

        log.info("===== JOB FINISHED =====");
        executor.shutdown();
    }

}
