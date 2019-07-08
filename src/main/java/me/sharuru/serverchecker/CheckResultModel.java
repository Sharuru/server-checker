package me.sharuru.serverchecker;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class CheckResultModel extends HostsModel {

    private boolean pingTestResult = false;
    private String pingTestMemo;

    private boolean portTestResult = false;
    private String portTestMemo;

    private boolean wgetTestResult = false;
    private String wgetTestMemo;

    public void setBase(HostsModel model) {
        this.setHost(model.getHost());
        this.setUrl(model.getUrl());
        this.setPort(model.getPort());
        this.setMemo(model.getMemo());
    }

}
