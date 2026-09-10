package com.termux.terminal;

public final class TerminalPty {

    private TerminalPty() {
    }

    public static int createSubprocess(String cmd, String cwd, String[] args, String[] envVars, int[] processId, int rows, int columns) {
        return JNI.createSubprocess(cmd, cwd, args, envVars, processId, rows, columns);
    }

    public static int waitFor(int processId) {
        return JNI.waitFor(processId);
    }

    public static void close(int fileDescriptor) {
        JNI.close(fileDescriptor);
    }

}
