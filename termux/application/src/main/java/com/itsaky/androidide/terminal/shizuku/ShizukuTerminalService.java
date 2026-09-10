package com.itsaky.androidide.terminal.shizuku;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import androidx.annotation.Keep;
import com.termux.terminal.JNI;
import java.io.File;

public class ShizukuTerminalService extends IShizukuTerminalService.Stub {

    private Context mContext;

    public ShizukuTerminalService() {
    }

    @Keep
    public ShizukuTerminalService(Context context) {
        this.mContext = context;
        loadNativeLibrary();
    }

    private void loadNativeLibrary() {
        try {
            System.loadLibrary("termux");
        } catch (Throwable t1) {
            try {
                if (mContext != null && mContext.getApplicationInfo() != null && mContext.getApplicationInfo().nativeLibraryDir != null) {
                    File lib = new File(mContext.getApplicationInfo().nativeLibraryDir, "libtermux.so");
                    if (lib.exists()) {
                        System.load(lib.getAbsolutePath());
                    }
                }
            } catch (Throwable t2) {
            }
        }
    }

    @Override
    public void destroy() {
        System.exit(0);
    }

    @Override
    public void exit() {
        destroy();
    }

    @Override
    public ParcelFileDescriptor createSubprocess(
            String cmd,
            String cwd,
            String[] args,
            String[] env,
            int[] processId,
            int rows,
            int columns
    ) throws RemoteException {
        loadNativeLibrary();
        int[] pidArray = new int[1];
        String shellCmd = (cmd != null && !cmd.isEmpty()) ? cmd : "/system/bin/sh";
        String workingDir = cwd;
        if (workingDir == null || workingDir.isEmpty()) {
            workingDir = "/data/local/tmp";
        }
        File workDirFile = new File(workingDir);
        if (!workDirFile.exists() || !workDirFile.canExecute()) {
            workingDir = "/data/local/tmp";
        }
        int ptm = JNI.createSubprocess(shellCmd, workingDir, args, env, pidArray, rows, columns);
        if (ptm < 0) {
            throw new RemoteException("Failed to open pseudo-terminal: " + ptm);
        }
        if (processId != null && processId.length > 0) {
            processId[0] = pidArray[0];
        }
        try {
            return ParcelFileDescriptor.adoptFd(ptm);
        } catch (Exception e) {
            JNI.close(ptm);
            throw new RemoteException("Failed to adopt file descriptor: " + e.getMessage());
        }
    }

    @Override
    public void setPtyWindowSize(int fd, int rows, int columns) throws RemoteException {
        try {
            JNI.setPtyWindowSize(fd, rows, columns);
        } catch (Throwable t) {
            throw new RemoteException(t.getMessage());
        }
    }

    @Override
    public int waitFor(int processId) throws RemoteException {
        try {
            return JNI.waitFor(processId);
        } catch (Throwable t) {
            throw new RemoteException(t.getMessage());
        }
    }

    @Override
    public void finishIfRunning(int processId) throws RemoteException {
        try {
            Os.kill(processId, OsConstants.SIGKILL);
        } catch (ErrnoException e) {
        }
    }
}
