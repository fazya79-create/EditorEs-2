package com.itsaky.androidide.terminal.shizuku;

import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;

public final class PrivilegedPty implements Parcelable {

    public final int pid;
    public final ParcelFileDescriptor master;

    public PrivilegedPty(int pid, ParcelFileDescriptor master) {
        this.pid = pid;
        this.master = master;
    }

    private PrivilegedPty(Parcel in) {
        this.pid = in.readInt();
        this.master = ParcelFileDescriptor.CREATOR.createFromParcel(in);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(pid);
        master.writeToParcel(dest, flags);
    }

    @Override
    public int describeContents() {
        return CONTENTS_FILE_DESCRIPTOR;
    }

    public static final Creator<PrivilegedPty> CREATOR = new Creator<PrivilegedPty>() {
        @Override
        public PrivilegedPty createFromParcel(Parcel in) {
            return new PrivilegedPty(in);
        }

        @Override
        public PrivilegedPty[] newArray(int size) {
            return new PrivilegedPty[size];
        }
    };

}
