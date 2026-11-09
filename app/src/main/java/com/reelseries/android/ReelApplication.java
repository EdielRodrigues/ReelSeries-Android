package com.reelseries.android;
public class ReelApplication extends android.app.Application {
 @Override public void onCreate(){super.onCreate();AccessGuard.install(this);}
}
