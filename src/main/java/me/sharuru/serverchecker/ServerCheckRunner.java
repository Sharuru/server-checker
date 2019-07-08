package me.sharuru.serverchecker;

import com.opencsv.bean.CsvToBeanBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.FileReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class ServerCheckRunner implements CommandLineRunner {

    @Autowired
    private ApplicationContext applicationContext;

    private List<SettingModel> fileLines = new ArrayList<>(0);

    @Override
    public void run(String... args) throws Exception {
        log.info("===== JOB STARTED =====");
        log.info("Step 0. Read setting file");
        fileLines = new CsvToBeanBuilder(new FileReader("D:\\hosts.txt")).withType(SettingModel.class).build().parse();
        log.info("Step 1. Check server using PING");

        for (SettingModel model : fileLines) {
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
                log.error("{} is not usable.", model.getIp());
            } else {
                log.info("Avg. " + (double) time / (5 - errorCount) + " ms");
            }

        }

        log.info("Step 2. Port check");
        for (SettingModel model : fileLines) {
            Socket s = new Socket();
            try {
                log.info("Connect {}, // {}", model.getIp(), model.getMemo());
                s.connect(new InetSocketAddress(model.getIp(), model.getPort()), 2000);
                s.setSoTimeout(2000);
            } catch (Exception e) {
                log.error("{} Not good", model.getIp());
                //e.printStackTrace();
            } finally {
                try {
                    s.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }


        log.info("===== JOB FINISHED =====");
    }
}
