package com.routix.app;

import android.app.Application;

public final class RoutixApp extends Application {
    @Override public void onCreate(){super.onCreate();DiagnosticLog.init(this);}
}
