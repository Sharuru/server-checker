package me.sharuru.serverchecker;

import com.opencsv.bean.CsvBindByName;
import lombok.Data;

@Data
public class HostsModel {

    @CsvBindByName(column = "ip")
    private String ip;

    @CsvBindByName(column = "url")
    private String url;

    @CsvBindByName(column = "port")
    private int port;

    @CsvBindByName(column = "memo")
    private String memo;

}
