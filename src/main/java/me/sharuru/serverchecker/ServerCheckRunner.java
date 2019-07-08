package me.sharuru.serverchecker;

import com.opencsv.bean.CsvToBeanBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Component
public class ServerCheckRunner implements CommandLineRunner {

    private Map<String, CheckResultModel> checkResultList = new HashMap<>();
    private List<CompletableFuture<Integer>> taskFutures = new ArrayList<>();

    private static final int THREADPOOL_SIZE = 3;
    private static final int PING_TIMES = 5;
    private static final int CONNECTION_TIMEOUT = 2000;

    @Override
    public void run(String... args) throws Exception {
        log.info("===== JOB STARTED =====");

        log.info("Step 0. Read setting file.");
        @SuppressWarnings("unchecked")
        List<HostsModel> hostList = new CsvToBeanBuilder(Files.newBufferedReader(Paths.get(("D:\\hosts.txt")))).withType(HostsModel.class).build().parse();

        ExecutorService executor = Executors.newFixedThreadPool(THREADPOOL_SIZE);

        log.info("Step 1. Check server using Ping.");
        for (HostsModel model : hostList) {
            CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
                try {
                    long latency = 0L;
                    int errorTimes = 0;
                    log.info("Now ping check on: {}, {}", model.getHost(), model.getMemo());
                    for (int i = 0; i < PING_TIMES; i++) {
                        long currentTime = System.currentTimeMillis();
                        boolean isPinged;
                        isPinged = InetAddress.getByName(model.getHost()).isReachable(CONNECTION_TIMEOUT);
                        currentTime = System.currentTimeMillis() - currentTime;
                        if (isPinged) {
                            latency += currentTime;
                        } else {
                            errorTimes++;
                        }
                        TimeUnit.SECONDS.sleep(1L);
                    }
                    if (errorTimes == PING_TIMES) {
                        CheckResultModel result = new CheckResultModel();
                        result.setBase(model);
                        result.setPingTestResult(false);
                        result.setPingTestMemo("Ping check is failed, 0/5.");
                        checkResultList.putIfAbsent(String.valueOf(model.hashCode()), result);
                    } else {
                        CheckResultModel result = new CheckResultModel();
                        result.setBase(model);
                        result.setPingTestResult(true);
                        result.setPingTestMemo("Ping check passed, average latency: " + (double) latency / (PING_TIMES - errorTimes) + "ms, " + (PING_TIMES - errorTimes) + "/" + PING_TIMES);
                        checkResultList.putIfAbsent(String.valueOf(model.hashCode()), result);
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
                    log.info("Now ping check on: {}, {}", model.getHost(), model.getMemo());
                    socketClient.connect(new InetSocketAddress(model.getHost(), model.getPort()), CONNECTION_TIMEOUT);
                    socketClient.setSoTimeout(CONNECTION_TIMEOUT);
                    checkResultList.get(String.valueOf(model.hashCode())).setPortTestResult(true);
                    checkResultList.get(String.valueOf(model.hashCode())).setPortTestMemo("Port check passed.");
                } catch (Exception e) {
                    checkResultList.get(String.valueOf(model.hashCode())).setPortTestResult(false);
                    checkResultList.get(String.valueOf(model.hashCode())).setPortTestMemo("Port check failed. " + e.getMessage());
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

        checkResultList.forEach((k, v) -> {
            log.info(v.getHost() + " || " + v.getPort() + " || " + v.toString());
        });

        log.info("===== JOB FINISHED =====");

        executor.shutdown();
    }

}
