package me.sharuru.serverchecker;

import com.opencsv.bean.CsvBindByName;
import lombok.Data;

@Data
public class HostsModel {

    @CsvBindByName(column = "host")
    private String host;

    @CsvBindByName(column = "url")
    private String url;

    @CsvBindByName(column = "port")
    private int port;

    @CsvBindByName(column = "memo")
    private String memo;

    public String getIdentify(){
        return this.host + "#" + this.url + "#" + this.port + "#" + this.memo;
    }

}
