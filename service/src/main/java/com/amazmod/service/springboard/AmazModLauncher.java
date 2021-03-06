package com.amazmod.service.springboard;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.LevelListDrawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.amazmod.service.BuildConfig;
import com.amazmod.service.Constants;
import com.amazmod.service.MainService;
import com.amazmod.service.R;
import com.amazmod.service.events.incoming.SyncSettings;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import clc.sliteplugin.flowboard.AbstractPlugin;
import clc.sliteplugin.flowboard.ISpringBoardHostStub;

public class AmazModLauncher extends AbstractPlugin {

    private Context mContext;
    private View view;
    private boolean isActive = false;
    private ISpringBoardHostStub host = null;

    private WidgetSettings settingsWidget;

    private TextView version, timeSLC, battValueTV;
    private ImageView battIconImg, imageView;

    @Override
    public View getView(final Context paramContext) {

        this.mContext = paramContext;

        mContext.startService(new Intent(paramContext, MainService.class));

        this.view = LayoutInflater.from(mContext).inflate(R.layout.amazmod_launcher, null);

        version = view.findViewById(R.id.launcher_version);
        timeSLC = view.findViewById(R.id.launcher_time_since_last_charge);
        battValueTV = view.findViewById(R.id.launcher_batt_value);
        battIconImg = view.findViewById(R.id.launcher_batt_icon);
        imageView = view.findViewById(R.id.launcher_logo);

        Log.d(Constants.TAG, "AmazModLauncher getView mContext: " + mContext.toString() + " / this: " + this.toString());

        version.setText(BuildConfig.VERSION_NAME);

        imageView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Intent intent = new Intent(mContext, WearGridActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_NEW_DOCUMENT |
                        Intent.FLAG_ACTIVITY_CLEAR_TOP |
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                //intent.putExtras(notificationSpec.toBundle());
                mContext.startActivity(intent);
                return true;
            }
        });

        //Initialize settings
        settingsWidget = new WidgetSettings(Constants.TAG, mContext);


        Log.d(Constants.TAG, "AmazModLauncher getView packagename: " + mContext.getPackageName()
                + " filesDir: " + mContext.getFilesDir() + " cacheDir: " + mContext.getCacheDir());

        return this.view;
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void settingsSync(SyncSettings event) {
        Log.w(Constants.TAG, "AmazModLauncher SyncSettings ***** event received *****");
    }

    private void updateTimeSinceLastCharge() {
        //Refresh saved data
        settingsWidget.reload();

        //Intent batteryStatus = mContext.registerReceiver(null, ifilter);
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = mContext.registerReceiver(null, ifilter);

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int batteryIconId = batteryStatus.getIntExtra(BatteryManager.EXTRA_ICON_SMALL, 0);

        //Set battery icon and text
        int battery = Math.round((level / (float)scale) * 100f);
        if (battery != 0) {
            String battlvl = Integer.toString(battery) + "%";
            battValueTV.setText(battlvl);
        } else {
            battValueTV.setText("N/A%");
        }

        LevelListDrawable batteryLevel = (LevelListDrawable) mContext.getResources().getDrawable(batteryIconId);
        batteryLevel.setLevel(level);
        battIconImg.setImageDrawable(batteryLevel);


        //Get date of last full charge
        long lastChargeDate = settingsWidget.get(Constants.PREF_DATE_LAST_CHARGE, 0L);

        StringBuilder dateDiff = new StringBuilder("  ");

        Log.d(Constants.TAG, "AmazModLauncher updateTimeSinceLastCharge level: " + level
                + " / scale: " + scale + " / batteryIconId: " + batteryIconId + " /" + dateDiff + lastChargeDate);

        //Log.d(Constants.TAG, "AmazModWidget updateTimeSinceLastChargeDate data: " + battery + " / " + lastChargeDate );
        if (lastChargeDate != 0L) {
            long diffInMillies = System.currentTimeMillis() - lastChargeDate;
            List<TimeUnit> units = new ArrayList<>(EnumSet.allOf(TimeUnit.class));
            Collections.reverse(units);
            long millisRest = diffInMillies;
            for (TimeUnit unit : units) {
                long diff = unit.convert(millisRest, TimeUnit.MILLISECONDS);
                long diffInMilliesForUnit = unit.toMillis(diff);
                millisRest = millisRest - diffInMilliesForUnit;
                if (unit.equals(TimeUnit.DAYS)) {
                    dateDiff.append(diff).append("d : ");
                } else if (unit.equals(TimeUnit.HOURS)) {
                    dateDiff.append(diff).append("h : ");
                } else if (unit.equals(TimeUnit.MINUTES)) {
                    dateDiff.append(diff).append("m");
                    break;
                }
            }
            dateDiff.append("\n").append(mContext.getResources().getText(R.string.last_charge));
        } else dateDiff.append(mContext.getResources().getText(R.string.last_charge_no_info));

        timeSLC.setText(dateDiff.toString());
    }

    private void refreshView() {
        updateTimeSinceLastCharge();
    }

    private void onShow() {
        // If view loaded (and was inactive)
        if (this.view != null && !this.isActive) {
            // If not the correct view
                // Refresh the view
                this.refreshView();
        }

        // Save state
        this.isActive = true;
    }

    private void onHide() {
        // Save state
        this.isActive = false;
        //Log.d(Constants.TAG, "AmazModPage onHide");
    }

    @Override
    public void onInactive(Bundle paramBundle) {
        super.onInactive(paramBundle);
        this.onHide();
    }
    @Override
    public void onPause() {
        super.onPause();
        this.onHide();
    }
    @Override
    public void onStop() {
        super.onStop();
        this.onHide();
    }

    @Override
    public void onActive(Bundle paramBundle) {
        super.onActive(paramBundle);
        this.onShow();
    }

    @Override
    public void onResume() {
        super.onResume();
        this.onShow();
    }

    /*
     * Below there are commented functions that the widget should have
     */

    // Return the icon for this page, used when the page is disabled in the app list. In this case, the launcher icon is used
    @Override
    public Bitmap getWidgetIcon(Context paramContext) {
        return ((BitmapDrawable) this.mContext.getResources().getDrawable(R.mipmap.ic_launcher)).getBitmap();
    }

    // Return the launcher intent for this page. This might be used for the launcher as well when the page is disabled?
    @Override
    public Intent getWidgetIntent() {
        //Intent localIntent = new Intent();
        //localIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        //localIntent.setAction("android.intent.action.MAIN");
        //localIntent.addCategory("android.intent.category.LAUNCHER");
        //localIntent.setComponent(new ComponentName(this.mContext.getPackageName(), "com.huami.watch.deskclock.countdown.CountdownListActivity"));
        return new Intent();
    }


    // Return the title for this page, used when the page is disabled in the app list. In this case, the app name is used
    @Override
    public String getWidgetTitle(Context paramContext) {
        return this.mContext.getResources().getString(R.string.launcher_name);
    }

    // Returns the springboard host
    public ISpringBoardHostStub getHost() {
        return this.host;
    }

    // Called when the page is loading and being bound to the host
    @Override
    public void onBindHost(ISpringBoardHostStub paramISpringBoardHostStub) {
        this.host = paramISpringBoardHostStub;
    }

    // Not sure what this does, can't find it being used anywhere. Best leave it alone
    @Override
    public void onReceiveDataFromProvider(int paramInt, Bundle paramBundle) {
        super.onReceiveDataFromProvider(paramInt, paramBundle);
    }

    // Called when the page is destroyed completely (in app mode). Same as the onDestroy method of an activity
    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
