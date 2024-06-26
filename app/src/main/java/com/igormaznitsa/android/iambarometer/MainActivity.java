package com.igormaznitsa.android.iambarometer;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

  private static final float MIN_MEASURABLE_PRESSURE_HPA = 946.0f;
  private static final float MAX_MEASURABLE_PRESSURE_HPA = 1053.0f;
  private static final float SCALE_MAX_ANGLE = 280.0f;

  private static final double TEMPERATURE_COEFF = 0.003665d;
  private static final double PRESSURE_SEA_LEVEL_HPA = 1013.25d;
  private static final String PROPERTY_CALIBRATED_ALTITUDE_OFFSET_METERS =
      "altitude.calibrated.offset.meters";
  private static final String PROPERTY_CALIBRATED_SEA_LEVEL_HPA =
      "altitude.calibrated.sea.level.hpa";
  private static final double TEMPERATURE_AVERAGE = 15.0d;
  private SensorManager sensorManager = null;
  private List<Sensor> sensorList = Collections.emptyList();
  private ImageView imageArrowView;
  private ImageView imageSeaLevelView;
  private TextView textViewHpa;
  private TextView textViewMmHg;
  private TextView textViewAlt;
  private volatile double calibratedDeltaMeters = 0.0d;
  private volatile float calibratedSeaZeroHpa = Float.NaN;
  private volatile float lastPressureHpa = Float.NaN;
  private final SensorEventListener sensorEventListener = new SensorEventListener() {
    @Override
    public void onSensorChanged(SensorEvent event) {
      final float valueHpa = event.values[0];
      lastPressureHpa = valueHpa;
      final float rotateAngle = calcIndicatorRotateAngleForHpa(valueHpa);

      final ImageView locImageView = imageArrowView;
      final ImageView locImageSeaLevelView = imageSeaLevelView;
      final TextView locTextViewHpa = textViewHpa;
      final TextView locTextViewMmHg = textViewMmHg;
      final TextView locTextViewAlt = textViewAlt;
      final float locCalibratedSeaZeroHpa = calibratedSeaZeroHpa;

      if (locImageView != null) {
        runOnUiThread(() -> {
          imageArrowView.setRotation(rotateAngle);
          if (locImageSeaLevelView != null) {
            if (Float.isNaN(locCalibratedSeaZeroHpa)) {
              locImageSeaLevelView.setVisibility(View.INVISIBLE);
            } else {
              locImageSeaLevelView.setVisibility(View.VISIBLE);
              locImageSeaLevelView.setRotation(
                  calcIndicatorRotateAngleForHpa(locCalibratedSeaZeroHpa));
            }
          }
          if (locTextViewHpa != null) {
            locTextViewHpa.setText(String.format(Locale.ENGLISH, "%.2f hPa", valueHpa));
          }
          if (locTextViewMmHg != null) {
            locTextViewMmHg.setText(
                String.format(Locale.ENGLISH, "%.2f mmHg", convertHpaTommHg(valueHpa)));
          }
          if (locTextViewAlt != null) {
            final double currentCalibration = calibratedDeltaMeters;
            final double altitude = Double.isNaN(currentCalibration) ? calcRawAltitudeMeters(valueHpa) :
                (calcRawAltitudeMeters(valueHpa) + currentCalibration);

            locTextViewAlt.setText(String.format(Locale.ENGLISH, "%.2f m", altitude));
          }
        });
      }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
  };

  private static float calcIndicatorRotateAngleForHpa(final float hpa) {
    final float indicatorPressureHpa = Math.min(MAX_MEASURABLE_PRESSURE_HPA, Math.max(
        MIN_MEASURABLE_PRESSURE_HPA, hpa));
    return ((indicatorPressureHpa - MIN_MEASURABLE_PRESSURE_HPA) / (
        MAX_MEASURABLE_PRESSURE_HPA -
            MIN_MEASURABLE_PRESSURE_HPA)) * SCALE_MAX_ANGLE;
  }

  private static float convertHpaTommHg(final float hpa) {
    return hpa * 0.750063755419211f;
  }

  private static double calcRawHpaForMeters(final double meters) {
    return Math.pow(10.0d, Math.log10(PRESSURE_SEA_LEVEL_HPA) - (meters / 18400.0d / (1 + TEMPERATURE_COEFF * TEMPERATURE_AVERAGE)));
  }

  private static double calcRawAltitudeMeters(final float hpa) {
    return (18400.0d * (1 + TEMPERATURE_COEFF * TEMPERATURE_AVERAGE) * Math.log10(PRESSURE_SEA_LEVEL_HPA / hpa));
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    this.sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
    this.sensorList = this.sensorManager.getSensorList(Sensor.TYPE_PRESSURE);
  }

  @Override
  protected void onResume() {
    try {
      super.onResume();
      this.imageArrowView = findViewById(R.id.imageArrow);
      this.imageSeaLevelView = findViewById(R.id.imageSeaIndicator);
      this.textViewHpa = findViewById(R.id.textViewHpa);
      this.textViewMmHg = findViewById(R.id.textViewMmHg);
      this.textViewAlt = findViewById(R.id.textViewAlt);
    } finally {
      this.loadPreferences();

      if (this.sensorList.isEmpty()) {
        Toast.makeText(getBaseContext(), getText(R.string.cant_find_barometer), Toast.LENGTH_LONG)
            .show();
      } else {
        this.sensorManager.registerListener(this.sensorEventListener, this.sensorList.get(0),
            SensorManager.SENSOR_DELAY_NORMAL);
      }
    }
  }

  private void enterAltitude() {
    AlertDialog.Builder alert = new AlertDialog.Builder(this);
    alert.setTitle(R.string.current_altitude_meters);
    final EditText input = new EditText(this);
    input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED |
        InputType.TYPE_NUMBER_FLAG_DECIMAL);
    input.setMaxLines(1);
    input.setRawInputType(Configuration.KEYBOARD_12KEY);
    alert.setView(input);
    alert.setPositiveButton(getText(R.string.ok), (dialog, whichButton) -> {
      try {
        final String text = input.getText().toString().trim();
        if (text.isEmpty()) {
          resetPreferences();
        } else {
          final float detectedPressureHpa = this.lastPressureHpa;
          final int value = Integer.parseInt(input.getText().toString());
          final double altitudeMeters = calcRawAltitudeMeters(detectedPressureHpa);
          final double deltaMeters = value - altitudeMeters;
          final float seaLevelPressure = (float) calcRawHpaForMeters(altitudeMeters - value);
          savePreferences(deltaMeters, seaLevelPressure);
        }
      } catch (Exception ex) {
        Toast.makeText(this, "Error during number enter", Toast.LENGTH_SHORT).show();
        Log.e(MainActivity.class.getSimpleName(), "Can't save preferences", ex);
      }
    });
    alert.setNegativeButton(R.string.cancel, (dialog, whichButton) -> {
      // do nothing
    });
    alert.show();
  }

  @Override
  protected void onPause() {
    try {
      super.onPause();
    } finally {

      this.imageArrowView = null;
      this.imageSeaLevelView = null;
      this.textViewAlt = null;
      this.textViewMmHg = null;
      this.textViewHpa = null;

      if (!this.sensorList.isEmpty()) {
        this.sensorManager.unregisterListener(this.sensorEventListener);
      }
    }
  }

  private void showInfoDialog(final CharSequence title, final CharSequence message) {
    new AlertDialog.Builder(MainActivity.this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(getText(R.string.ok),
            (dialog, which) -> dialog.dismiss())
        .show();
  }

  private void loadPreferences() {
    final SharedPreferences sharedPreferences = getPreferences(Context.MODE_PRIVATE);
    this.calibratedSeaZeroHpa =
        sharedPreferences.getFloat(PROPERTY_CALIBRATED_SEA_LEVEL_HPA, Float.NaN);
    this.calibratedDeltaMeters =
        sharedPreferences.contains(PROPERTY_CALIBRATED_ALTITUDE_OFFSET_METERS) ?
            Double.longBitsToDouble(
                sharedPreferences.getLong(PROPERTY_CALIBRATED_ALTITUDE_OFFSET_METERS, Double.doubleToLongBits(Double.NaN))) : Double.NaN;
    Log.i(MainActivity.class.getSimpleName(),
        "Loaded preferences: calibratedDeltaMeters=" + this.calibratedDeltaMeters);
  }

  private void resetPreferences() {
    try {
      this.calibratedDeltaMeters = Double.NaN;
      this.calibratedSeaZeroHpa = Float.NaN;
      final SharedPreferences sharedPreferences = getPreferences(Context.MODE_PRIVATE);
      sharedPreferences.edit()
          .clear()
          .apply();
    } catch (Exception ex) {
      Toast.makeText(this, "Error during property reset", Toast.LENGTH_LONG).show();
      Log.e(MainActivity.class.getSimpleName(), "Can't reset preferences", ex);
    }
  }

  private void savePreferences(
      final double deltaMeters, final float calibratedZeroSeaLevelHpa) {
    try {
      this.calibratedDeltaMeters = deltaMeters;
      this.calibratedSeaZeroHpa = calibratedZeroSeaLevelHpa;
      final SharedPreferences sharedPreferences = getPreferences(Context.MODE_PRIVATE);

      sharedPreferences.edit()
          .putLong(PROPERTY_CALIBRATED_ALTITUDE_OFFSET_METERS,
              Double.doubleToLongBits(deltaMeters))
          .putFloat(PROPERTY_CALIBRATED_SEA_LEVEL_HPA, calibratedZeroSeaLevelHpa)
          .apply();
    } catch (Exception ex) {
      Toast.makeText(this, "Error during property save", Toast.LENGTH_LONG).show();
      Log.e(MainActivity.class.getSimpleName(), "Can't save preferences", ex);
    }
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    String version = "";

    try {
      version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
    } catch (Exception ex) {
      Log.e(MainActivity.class.getSimpleName(), "Error during get version", ex);
    }

    final int itemId = item.getItemId();

    if (itemId == R.id.action_set_altitude) {
      this.enterAltitude();
      return true;
    } else if (itemId == R.id.action_about) {
      this.showInfoDialog(getText(R.string.menu_about),
          String.format(getString(R.string.about_text), version));
      return true;
    } else if (itemId == R.id.action_exit) {
      this.finishAndRemoveTask();
      return true;
    } else {
      return super.onOptionsItemSelected(item);
    }
  }


  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    menu.findItem(R.id.action_set_altitude).setVisible(!this.sensorList.isEmpty());
    return true;
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.main, menu);
    return true;
  }

}