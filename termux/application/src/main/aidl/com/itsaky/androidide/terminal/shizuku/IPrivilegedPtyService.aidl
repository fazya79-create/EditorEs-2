package com.itsaky.androidide.terminal.shizuku;

import com.itsaky.androidide.terminal.shizuku.PrivilegedPty;

interface IPrivilegedPtyService {

    void destroy() = 16777114;

    PrivilegedPty createPty(String cmd, String cwd, in String[] args, in String[] env, int rows, int columns) = 1;

    int waitFor(int pid) = 2;

    void kill(int pid, int signal) = 3;

    String getCwd(int pid) = 4;

    int getUid() = 5;
}
