package com.skypine.common;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Re-implementation of skypine's FM/HD-radio Parcelable to read the "fm_key"
 * extra from com.skypine.main.updateFM. Leading field order must match skypine:
 * band, frequency, title (RDS radiotext), artist (RDS). Reading a subset is safe.
 */
public class FMInfo implements Parcelable {
    public String band;
    public String frequency;
    public String title;
    public String artist;

    public FMInfo() {}

    protected FMInfo(Parcel in) {
        band = in.readString();
        frequency = in.readString();
        title = in.readString();
        artist = in.readString();
    }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel d, int f) {
        d.writeString(band);
        d.writeString(frequency);
        d.writeString(title);
        d.writeString(artist);
    }

    public static final Creator<FMInfo> CREATOR = new Creator<FMInfo>() {
        public FMInfo createFromParcel(Parcel in) { return new FMInfo(in); }
        public FMInfo[] newArray(int size) { return new FMInfo[size]; }
    };
}
