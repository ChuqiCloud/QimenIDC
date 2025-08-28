package com.chuqiyun.proxmoxveams.entity;

public class CNat {
    private Integer id;
    private String nataddr;
    private String natbridge;
    private String addrdomain;
    private String dns1;
    private String dns2;
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getNataddr() {
        return nataddr;
    }

    public void setNatAddr(String nataddr) {
        this.nataddr = nataddr;
    }

    public String getNatbridge() {
        return natbridge;
    }

    public void setNatBridge(String natbridge) {
        this.natbridge = natbridge;
    }
    public String getAddrdomain() {
        return addrdomain;
    }

    public void setAddrdomain(String addrdomain) {
        this.addrdomain = addrdomain;
    }

    public String getDns1() {
        return dns1;
    }

    public void setDns1(String dns1) {
        this.dns1 = dns1;
    }

    public String getDns2() {
        return dns2;
    }

    public void setDns2(String dns2) {
        this.dns2 = dns2;
    }
}
