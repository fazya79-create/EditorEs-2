package com.termux.terminal;

import java.io.IOException;

public interface TerminalProcess {

    interface Launcher {
        TerminalProcess launch(String shellPath, String cwd, String[] args, String[] env, int rows, int columns);
    }

    int getPid();

    int getFileDescriptor();

    int waitFor();

    void kill();

    void close();

    String getCwd() throws IOException;

}
