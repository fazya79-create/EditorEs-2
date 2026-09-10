package com.itsaky.androidide.terminal.shizuku;

import android.content.Context;
import android.os.Binder;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import androidx.annotation.Keep;

import com.termux.terminal.TerminalPty;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

@Keep
public final class PrivilegedPtyService extends IPrivilegedPtyService.Stub {

    private static final String LOG_TAG = "PrivilegedPtyService";

    private final Set<Integer> mChildren = new HashSet<>();
    private final int mClientUid;

    @Keep
    public PrivilegedPtyService() {
        this(null);
    }

    @Keep
    public PrivilegedPtyService(Context context) {
        mClientUid = context != null ? context.getApplicationInfo().uid : -1;
        Log.i(LOG_TAG, "Started with uid=" + Os.getuid() + ", client uid=" + mClientUid);
    }

    @Override
    public void destroy() {
        Integer[] children;
        synchronized (mChildren) {
            children = mChildren.toArray(new Integer[0]);
            mChildren.clear();
        }
        for (int pid : children) {
            signal(pid, OsConstants.SIGKILL);
        }
        System.exit(0);
    }

    @Override
    public PrivilegedPty createPty(String cmd, String cwd, String[] args, String[] env, int rows, int columns) {
        enforceClient();
        int[] processId = new int[1];
        int master;
        try {
            master = TerminalPty.createSubprocess(cmd, cwd, args, env, processId, rows, columns);
        } catch (RuntimeException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        synchronized (mChildren) {
            mChildren.add(processId[0]);
        }
        return new PrivilegedPty(processId[0], ParcelFileDescriptor.adoptFd(master));
    }

    @Override
    public int waitFor(int pid) {
        enforceClient();
        if (!isChild(pid)) return -1;
        int status = TerminalPty.waitFor(pid);
        synchronized (mChildren) {
            mChildren.remove(pid);
        }
        return status;
    }

    @Override
    public void kill(int pid, int signal) {
        enforceClient();
        if (!isChild(pid)) return;
        signal(pid, signal);
    }

    @Override
    public String getCwd(int pid) {
        enforceClient();
        if (!isChild(pid)) return null;
        try {
            String cwdSymlink = String.format("/proc/%s/cwd/", pid);
            String outputPath = new File(cwdSymlink).getCanonicalPath();
            String outputPathWithTrailingSlash = outputPath.endsWith("/") ? outputPath : outputPath + "/";
            if (!cwdSymlink.equals(outputPathWithTrailingSlash)) {
                return outputPath;
            }
        } catch (IOException | SecurityException e) {
            Log.w(LOG_TAG, "Error getting current directory of " + pid, e);
        }
        return null;
    }

    @Override
    public int getUid() {
        enforceClient();
        return Os.getuid();
    }

    private void enforceClient() {
        int callingUid = Binder.getCallingUid();
        if (mClientUid != -1 && callingUid != mClientUid && callingUid != Os.getuid()) {
            throw new SecurityException("Caller uid " + callingUid + " is not allowed");
        }
    }

    private boolean isChild(int pid) {
        synchronized (mChildren) {
            return mChildren.contains(pid);
        }
    }

    private static void signal(int pid, int signal) {
        try {
            Os.kill(pid, signal);
        } catch (ErrnoException e) {
            Log.w(LOG_TAG, "Failed sending signal " + signal + " to " + pid + ": " + e.getMessage());
        }
    }

}
