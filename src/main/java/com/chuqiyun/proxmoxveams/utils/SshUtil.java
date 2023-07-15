package com.chuqiyun.proxmoxveams.utils;

import com.jcraft.jsch.*;

import java.io.OutputStream;

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
