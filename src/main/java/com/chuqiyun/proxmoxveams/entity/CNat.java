package com.chuqiyun.proxmoxveams.entity;

public class CNat {
    private Integer id;
    private String nataddr;
    private String natbridge;
    private String addrdomain;
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getNataddr() {
        return nataddr;
    }

    public void setNataddr(String nataddr) {
        this.nataddr = nataddr;
    }

    public String getNatbridge() {
        return natbridge;
    }

    public void setNatbridge(String natbridge) {
        this.natbridge = natbridge;
    }
    public String getAddrdomain() {
        return addrdomain;
    }

    public void setAddrdomain(String addrdomain) {
        this.addrdomain = addrdomain;
    }
}
