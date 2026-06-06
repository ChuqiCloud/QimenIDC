package com.chuqiyun.proxmoxveams.utils;

import com.jcraft.jsch.*;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * @author mryunqi
 * @date 2023/6/20
 */
public class SshUtil {
    private final String hostname;
    private final int port;
    private final String username;
    private final String password;
    private Session session;

    public SshUtil(String hostname, int port, String username, String password) {
        this.hostname = hostname;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    public void connect() throws JSchException {
        JSch jsch = new JSch();
        session = jsch.getSession(username, hostname, port);
        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect();
    }

    public String executeCommand(String command) throws JSchException, InterruptedException {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        channel.setInputStream(null);
        channel.setErrStream(System.err);

        StringBuilder output = new StringBuilder();
        channel.setOutputStream(new CustomOutputStream(output));

        channel.connect();
        channel.disconnect();

        return output.toString();
    }

    /**
     * @Author: 星禾
     * @Description: 通过SFTP上传文本文件
     * @DateTime: 2026/6/6 12:40
     */
    public void uploadTextFile(String remotePath, String content) throws JSchException, SftpException {
        ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
        channel.connect();
        try {
            createRemoteDirectories(channel, getParentPath(remotePath));
            ByteArrayInputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
            channel.put(inputStream, remotePath);
        } finally {
            channel.disconnect();
        }
    }

    private String getParentPath(String remotePath) {
        int index = remotePath.lastIndexOf('/');
        if (index <= 0) {
            return "/";
        }
        return remotePath.substring(0, index);
    }

    private void createRemoteDirectories(ChannelSftp channel, String remotePath) throws SftpException {
        if (remotePath == null || remotePath.trim().isEmpty() || "/".equals(remotePath)) {
            return;
        }
        StringBuilder currentPath = new StringBuilder();
        String[] pathItems = remotePath.split("/");
        for (String pathItem : pathItems) {
            if (pathItem == null || pathItem.trim().isEmpty()) {
                continue;
            }
            currentPath.append('/').append(pathItem);
            String path = currentPath.toString();
            try {
                channel.stat(path);
            } catch (SftpException e) {
                channel.mkdir(path);
            }
        }
    }

    public void disconnect() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }

    private static class CustomOutputStream extends OutputStream {
        private final StringBuilder output;

        public CustomOutputStream(StringBuilder output) {
            this.output = output;
        }

        @Override
        public void write(int b) {
            output.append((char) b);
        }
    }
}
