package com.igormaznitsa.android.iambarometer;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

  private static final float MIN_MEASURABLE_PRESSURE_HPA = 946.0f;
  private static final float MAX_MEASURABLE_PRESSURE_HPA = 1053.0f;
  private static final float SCALE_MAX_ANGLE = 280.0f;

  private static final double TEMPERATURE_COEFF = 0.003665d;
  private static final double PRESSURE_SEA_LEVEL_HPA = 1013.25d;


  private SensorManager sensorManager = null;
  private List<Sensor> sensorList = Collections.emptyList();
  private ImageView imageArrowView;
  private TextView textViewHpa;
  private TextView textViewMmHg;
  private TextView textViewAlt;

  private static double calcAltitudeMeters(final float hpa) {
    return 18400.0d*(1 + TEMPERATURE_COEFF * 15.0d) * Math.log10(PRESSURE_SEA_LEVEL_HPA/hpa);
  }

  private static float convertHpaTommHg(final float hpa) {
    return hpa * 0.750063755419211f;
  }

  private final SensorEventListener sensorEventListener = new SensorEventListener() {
    @Override
    public void onSensorChanged(SensorEvent event) {
      final float valueHpa = event.values[0];
      final float indicatorPressureHpa = Math.min(MAX_MEASURABLE_PRESSURE_HPA, Math.max(
          MIN_MEASURABLE_PRESSURE_HPA, valueHpa));
      final float rotateAngle = ((indicatorPressureHpa - MIN_MEASURABLE_PRESSURE_HPA) / (
          MAX_MEASURABLE_PRESSURE_HPA -
          MIN_MEASURABLE_PRESSURE_HPA)) * SCALE_MAX_ANGLE;

        final ImageView locImageView = imageArrowView;
        final TextView locTextViewHpa = textViewHpa;
        final TextView locTextViewMmHg = textViewMmHg;
        final TextView locTextViewAlt = textViewAlt;

        if (locImageView!=null) {
          runOnUiThread(() -> {
            imageArrowView.setRotation(rotateAngle);
            if (locTextViewHpa != null) {
              locTextViewHpa.setText(String.format(Locale.ENGLISH, "%.2f hPa", valueHpa));
            }
            if (locTextViewMmHg != null) {
              locTextViewMmHg.setText(String.format(Locale.ENGLISH, "%.2f mmHg",convertHpaTommHg(valueHpa)));
            }
            if (locTextViewAlt != null) {
              locTextViewAlt.setText(String.format(Locale.ENGLISH, "%.2f m",calcAltitudeMeters(valueHpa)));
            }
          });
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
  };

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
      this.textViewHpa = findViewById(R.id.textViewHpa);
      this.textViewMmHg = findViewById(R.id.textViewMmHg);
      this.textViewAlt = findViewById(R.id.textViewAlt);
    } finally {
      if (this.sensorList.isEmpty()) {
        Toast.makeText(getBaseContext(), "Can't find any air pressure sensor!", Toast.LENGTH_LONG).show();
      } else {
        this.sensorManager.registerListener(this.sensorEventListener, this.sensorList.get(0),
            SensorManager.SENSOR_DELAY_UI);
      }
    }
  }

  @Override
  protected void onPause() {
    try {
      super.onPause();
    } finally {

      this.imageArrowView = null;
      this.textViewAlt = null;
      this.textViewMmHg = null;
      this.textViewHpa = null;

      if (!this.sensorList.isEmpty()) {
        this.sensorManager.unregisterListener(this.sensorEventListener);
      }
    }
  }
}