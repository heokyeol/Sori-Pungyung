package dev.homesev.sounddrive;

/** Receives position fixes from the build's location source (DeviceLocation). */
interface LocationSink {
    void onLocation(double lat, double lon);
}
