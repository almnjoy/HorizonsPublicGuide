package com.skypine.common;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Re-implementation of skypine's now-playing Parcelable so our launcher can read
 * the "music_key" extra from the com.skypine.main.updateMusic / updateBTMusic /
 * updateCarPlayMusic / updateAAMusic broadcasts. Must match skypine's
 * writeToParcel field order for the leading fields (title, singer, albumName,
 * album). Reading a subset of the parcel is safe; trailing fields are ignored.
 */
public class MusicInfo implements Parcelable {
    public String title;
    public String singer;
    public String albumName;
    public String album;
    public int duration;
    public int position;

    public MusicInfo() {}

    protected MusicInfo(Parcel in) {
        title = in.readString();
        singer = in.readString();
        albumName = in.readString();
        album = in.readString();
        try { duration = in.readInt(); position = in.readInt(); } catch (Exception ignored) {}
    }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel d, int f) {
        d.writeString(title);
        d.writeString(singer);
        d.writeString(albumName);
        d.writeString(album);
        d.writeInt(duration);
        d.writeInt(position);
    }

    public static final Creator<MusicInfo> CREATOR = new Creator<MusicInfo>() {
        public MusicInfo createFromParcel(Parcel in) { return new MusicInfo(in); }
        public MusicInfo[] newArray(int size) { return new MusicInfo[size]; }
    };
}
