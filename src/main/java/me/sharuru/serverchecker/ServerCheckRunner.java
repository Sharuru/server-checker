package me.sharuru.serverchecker;

import com.opencsv.bean.CsvToBeanBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.FileReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ServerCheckRunner implements CommandLineRunner {

    private List<HostsModel> hostList = new ArrayList<>(0);
    private Map<String, CheckResultModel> checkResultList = new HashMap<>();

    @Override
    public void run(String... args) throws Exception {
        log.info("===== JOB STARTED =====");

        log.info("Step 0. Read setting file");
        hostList = new CsvToBeanBuilder(new FileReader("D:\\hosts.txt")).withType(HostsModel.class).build().parse();

        log.info("Step 1. Check server using PING");
        for (HostsModel model : hostList) {
            log.info("-----");
            long time = 0L;
            int errorCount = 0;
            log.info("Ping {}, // {}", model.getIp(), model.getMemo());
            for (int i = 0; i < 5; i++) {
                long currentTime = System.currentTimeMillis();
                boolean isPinged = InetAddress.getByName(model.getIp()).isReachable(2000); // 2 seconds
                currentTime = System.currentTimeMillis() - currentTime;
                if (isPinged) {
                    time += currentTime;
                } else {
                    errorCount++;
                }
                Thread.sleep(1000L);
            }
            if (errorCount == 5) {
                CheckResultModel result = new CheckResultModel();
                result.setUrl(model.getUrl());
                result.setIp(model.getIp());
                result.setPort(model.getPort());
                result.setMemo(model.getMemo());
                result.setPingTestResult(false);
                result.setPingTestMemo("PING test failed, 0/5");
                checkResultList.putIfAbsent(String.valueOf(model.hashCode()), result);
                //log.error("{} is not usable.", model.getIp());
            } else {
                //log.info("Avg. " + (double) time / (5 - errorCount) + " ms");
                CheckResultModel result = new CheckResultModel();
                result.setUrl(model.getUrl());
                result.setIp(model.getIp());
                result.setPort(model.getPort());
                result.setMemo(model.getMemo());
                result.setPingTestResult(true);
                result.setPingTestMemo("PING test passed, avg: " + (double) time / (5 - errorCount) + "ms, " + (5 - errorCount) + "/5");
                checkResultList.putIfAbsent(String.valueOf(model.hashCode()), result);
            }
        }

        log.info("Step 2. Port check");
        for (HostsModel model : hostList) {
            Socket s = new Socket();
            try {
                log.info("Connect {}, // {}", model.getIp(), model.getMemo());
                s.connect(new InetSocketAddress(model.getIp(), model.getPort()), 2000);
                s.setSoTimeout(2000);
                checkResultList.get(String.valueOf(model.hashCode())).setPortTestResult(true);
                //checkResultList.get(String.valueOf(model.hashCode())).setPortTestMemo("Port test passed.");
            } catch (Exception e) {
                checkResultList.get(String.valueOf(model.hashCode())).setPortTestResult(false);
                checkResultList.get(String.valueOf(model.hashCode())).setPortTestMemo("Port test failed." + e.getMessage());
                //log.error("{} Not good", model.getIp());
                //e.printStackTrace();
            } finally {
                try {
                    s.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        }

        checkResultList.forEach((k, v) -> {
            log.info(v.getIp() + "|| " + v.getPort() + " || " + v.toString());
        });

        log.info("===== JOB FINISHED =====");
    }

}
