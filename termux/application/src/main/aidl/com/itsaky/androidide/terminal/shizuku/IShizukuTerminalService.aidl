package com.itsaky.androidide.terminal.shizuku;

import android.os.ParcelFileDescriptor;

interface IShizukuTerminalService {
    void destroy() = 16777114;
    void exit() = 1;
    ParcelFileDescriptor createSubprocess(String cmd, String cwd, in String[] args, in String[] env, out int[] processId, int rows, int columns) = 2;
    void setPtyWindowSize(int fd, int rows, int columns) = 3;
    int waitFor(int processId) = 4;
    void finishIfRunning(int processId) = 5;
}
