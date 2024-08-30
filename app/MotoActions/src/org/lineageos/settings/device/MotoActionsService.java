/*
 * Copyright (c) 2015 The CyanogenMod Project
 * Copyright (c) 2017-2022 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.settings.device;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.UEventObserver;
import android.util.Log;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import org.lineageos.settings.device.actions.ChopChopSensor;
import org.lineageos.settings.device.actions.FlipToMute;
import org.lineageos.settings.device.actions.LiftToSilence;
import org.lineageos.settings.device.actions.ProximitySilencer;
import org.lineageos.settings.device.actions.UpdatedStateNotifier;
import org.lineageos.settings.device.doze.DozePulseAction;
import org.lineageos.settings.device.doze.FlatUpSensor;
import org.lineageos.settings.device.doze.ScreenStateNotifier;
import org.lineageos.settings.device.doze.StowSensor;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.LinkedList;
import java.util.List;

public class MotoActionsService extends Service implements ScreenStateNotifier,
        UpdatedStateNotifier {
    private static final String TAG = "MotoActions";
    private static final String CHARGE_CURRENT_FILE = "/sys/devices/platform/charger/power_supply/mtk-master-charger/constant_charge_current_max";

    private UEventObserver mObserver;

    private final List<ScreenStateNotifier> mScreenStateNotifiers = new LinkedList<>();
    private final List<UpdatedStateNotifier> mUpdatedStateNotifiers = new LinkedList<>();

    private PowerManager mPowerManager;
    private PowerManager.WakeLock mWakeLock;

    public void onCreate() {
        Log.d(TAG, "Starting");

        mObserver = new UEventObserver() {
            @Override
            public void onUEvent(UEvent event) {
                String chargerStatus = event.get("POWER_SUPPLY_ONLINE");
                if (chargerStatus != null && chargerStatus.equals("2")) {
                    updateChargeCurrent();
                }
            }
        };
        mObserver.startObserving("DEVPATH=/sys/class/power_supply/primary_chg");

        updateChargeCurrent();

        MotoActionsSettings actionsSettings = new MotoActionsSettings(this, this);
        SensorHelper sensorHelper = new SensorHelper(this);
        DozePulseAction dozePulseAction = new DozePulseAction(this);

        mScreenStateNotifiers.add(dozePulseAction);

        // Actionable sensors get screen on/off notifications
        mScreenStateNotifiers.add(new StowSensor(actionsSettings, sensorHelper, dozePulseAction));
        mScreenStateNotifiers.add(new FlatUpSensor(actionsSettings, sensorHelper, dozePulseAction));

        // Other actions that are always enabled
        mUpdatedStateNotifiers.add(new ChopChopSensor(actionsSettings, sensorHelper));
        mUpdatedStateNotifiers.add(new ProximitySilencer(actionsSettings, this, sensorHelper));
        mUpdatedStateNotifiers.add(new FlipToMute(actionsSettings, this, sensorHelper));
        mUpdatedStateNotifiers.add(new LiftToSilence(actionsSettings, this, sensorHelper));

        mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                TAG + ":WakeLock");

        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mScreenStateReceiver, filter);

        updateState();
    }

    private void updateChargeCurrent() {
        boolean turboEnabled = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("turbo_enable", false);
        Log.i(TAG, "isTurbo="+turboEnabled);
        String currentValue;
        if (!turboEnabled) {
            currentValue = "3000000";
        } else {
            currentValue = PreferenceManager.getDefaultSharedPreferences(this).getString("turbo_current", "5000000");
        }
        Log.i(TAG, "currentValue="+currentValue);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(CHARGE_CURRENT_FILE))) {
            Integer.parseInt(currentValue);
            writer.write(currentValue);
            Log.i(TAG, "Updated Charging current");
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid charge current value: " + currentValue, e);
        } catch (IOException e) {
            Log.e(TAG, "Failed to update charge current", e);
        }
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void screenTurnedOn() {
        if (!mWakeLock.isHeld()) {
            mWakeLock.acquire();
        }
        for (ScreenStateNotifier screenStateNotifier : mScreenStateNotifiers) {
            screenStateNotifier.screenTurnedOn();
        }
    }

    @Override
    public void screenTurnedOff() {
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        for (ScreenStateNotifier screenStateNotifier : mScreenStateNotifiers) {
            screenStateNotifier.screenTurnedOff();
        }
    }

    public void updateState() {
        if (mPowerManager.isInteractive()) {
            screenTurnedOn();
        } else {
            screenTurnedOff();
        }
        for (UpdatedStateNotifier notifier : mUpdatedStateNotifiers) {
            notifier.updateState();
        }
    }

    private final BroadcastReceiver mScreenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                screenTurnedOff();
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                screenTurnedOn();
            }
        }
    };
}
