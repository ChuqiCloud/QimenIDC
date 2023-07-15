package com.chuqiyun.proxmoxveams;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@MapperScan("com.chuqiyun.proxmoxveams.dao")
public class ProxmoxVeAmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProxmoxVeAmsApplication.class, args);
    }

}
