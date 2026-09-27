package android.car;

import android.content.Context;
import android.content.ServiceConnection;

/** COMPILE-ONLY stub matching the Android 9 android.car.Car API. NOT dexed;
 *  the real framework class (bootclasspath) is used at runtime. */
public class Car {
    public static final String PROPERTY_SERVICE = "property";
    public static Car createCar(Context context, ServiceConnection sc) { return null; }
    public void connect() {}
    public Object getCarManager(String serviceName) { return null; }
}
