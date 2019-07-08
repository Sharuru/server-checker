package me.sharuru.serverchecker;

import lombok.Data;

@Data
public class CheckResultModel extends HostsModel {

    private boolean pingTestResult = false;
    private String pingTestMemo;

    private boolean portTestResult = false;
    private String portTestMemo;

    private boolean wgetTestResult = false;
    private String wgetTestMemo;
    
}
