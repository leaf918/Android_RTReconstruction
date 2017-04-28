package net.zjucvg.rtreconstruction;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class OfflineSourceSettingsActivity extends AppCompatActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_offline_source_settings);
    if (null == savedInstanceState) {
      getFragmentManager()
          .beginTransaction()
          .replace(R.id.container, OfflineSourceSettingsFragment.newInstance())
          .commit();
    }
  }
}
